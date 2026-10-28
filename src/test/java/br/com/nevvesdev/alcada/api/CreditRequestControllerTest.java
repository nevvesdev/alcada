package br.com.nevvesdev.alcada.api;

import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.support.FakeCreditBureau;
import br.com.nevvesdev.alcada.application.support.FakeLimitReservation;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.application.usecase.GetCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ProcessCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ResolveManualReviewUseCase;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditRequestControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    private static final String BASE_PATH = "/api/v1/credit-requests";
    private static final String VALID_BODY =
            "{\"cpf\":\"529.982.247-25\",\"requestedAmount\":5000,\"declaredIncome\":4000}";

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final FakeCreditBureau bureau = new FakeCreditBureau();
    private final FakeLimitReservation limits = new FakeLimitReservation();
    private final MockMvc mockMvc = buildMockMvc(repository, bureau, limits);

    private static MockMvc buildMockMvc(CreditRequestRepository repository,
                                        FakeCreditBureau bureau,
                                        FakeLimitReservation limits) {
        CreditSagaOrchestrator orchestrator =
                new CreditSagaOrchestrator(repository, bureau, limits, RiskPolicy.standard(), CLOCK);
        CreditRequestController controller = new CreditRequestController(
                new OpenCreditRequestUseCase(repository, CLOCK),
                new ProcessCreditRequestUseCase(repository, orchestrator),
                new GetCreditRequestUseCase(repository),
                new ResolveManualReviewUseCase(repository, orchestrator));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ResultActions create(String key, String body) throws Exception {
        return mockMvc.perform(post(BASE_PATH)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createAndGetId(String key) throws Exception {
        String response = create(key, VALID_BODY).andReturn().getResponse().getContentAsString();
        int start = response.indexOf("\"id\":\"") + 6;
        return response.substring(start, start + 36);
    }

    @Test
    void shouldCreateApprovedRequest() throws Exception {
        bureau.returning(700);

        create("key-1", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cpf").value("***.982.247-**"))
                .andExpect(jsonPath("$.creditScore").value(700))
                .andExpect(jsonPath("$.riskVerdict").value("APPROVED"));
    }

    @Test
    void shouldCreateRejectedRequestWhenScoreIsTooLow() throws Exception {
        bureau.returning(300);

        create("key-1", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("LOW_SCORE"));
    }

    @Test
    void shouldCreateRequestUnderManualReview() throws Exception {
        bureau.returning(500);

        create("key-1", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void shouldNeverExposeFullCpf() throws Exception {
        bureau.returning(700);

        create("key-1", VALID_BODY)
                .andExpect(content().string(not(containsString("52998224725"))))
                .andExpect(content().string(not(containsString("529.982.247-25"))));
    }

    @Test
    void shouldReturnOkAndExistingRequestOnIdempotentReplay() throws Exception {
        bureau.returning(700);
        create("key-1", VALID_BODY).andExpect(status().isCreated());

        create("key-1", VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(repository.size()).isEqualTo(1);
        assertThat(bureau.calls()).isEqualTo(1);
    }

    @Test
    void shouldRejectKeyReuseWithDifferentPayload() throws Exception {
        bureau.returning(700);
        create("key-1", VALID_BODY).andExpect(status().isCreated());

        create("key-1", "{\"cpf\":\"529.982.247-25\",\"requestedAmount\":9000,\"declaredIncome\":4000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void shouldRejectRequestWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldReportValidationErrorsPerField() throws Exception {
        create("key-1", "{\"cpf\":\"\",\"requestedAmount\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").exists());
    }

    @Test
    void shouldRejectInvalidCpf() throws Exception {
        create("key-1", "{\"cpf\":\"111.111.111-11\",\"requestedAmount\":5000,\"declaredIncome\":4000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("CPF inválido"));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        create("key-1", "{")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldRejectUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_BODY))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturnAcceptedWhenSagaIsInterruptedByInfrastructureFailure() throws Exception {
        bureau.returning(700);
        limits.failConfirm(true);
        limits.failRelease(true);

        create("key-1", VALID_BODY)
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("LIMIT_RESERVED"));
    }

    @Test
    void shouldGetExistingRequest() throws Exception {
        bureau.returning(700);
        String id = createAndGetId("key-1");

        mockMvc.perform(get(BASE_PATH + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void shouldReturnNotFoundForUnknownRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + CreditRequestId.generate()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CREDIT_REQUEST_NOT_FOUND"));
    }

    @Test
    void shouldRejectMalformedIdentifier() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldApproveRequestUnderManualReview() throws Exception {
        bureau.returning(500);
        String id = createAndGetId("key-1");

        mockMvc.perform(post(BASE_PATH + "/" + id + "/review/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void shouldDenyRequestUnderManualReview() throws Exception {
        bureau.returning(500);
        String id = createAndGetId("key-1");

        mockMvc.perform(post(BASE_PATH + "/" + id + "/review/deny"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("REVIEW_DENIED"));
    }

    @Test
    void shouldRejectReviewOfRequestThatIsNotUnderReview() throws Exception {
        bureau.returning(700);
        String id = createAndGetId("key-1");

        mockMvc.perform(post(BASE_PATH + "/" + id + "/review/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void shouldReturnNotFoundWhenReviewingUnknownRequest() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/" + CreditRequestId.generate() + "/review/deny"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideInternalDetailsOnUnexpectedFailure() throws Exception {
        CreditRequestRepository failing = new CreditRequestRepository() {
            @Override
            public void save(CreditRequest request) {
                throw new IllegalStateException("segredo interno");
            }

            @Override
            public Optional<CreditRequest> findById(CreditRequestId id) {
                throw new IllegalStateException("segredo interno");
            }

            @Override
            public Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
                return Optional.empty();
            }
        };
        MockMvc failingMockMvc = buildMockMvc(failing, bureau, limits);

        failingMockMvc.perform(get(BASE_PATH + "/" + CreditRequestId.generate()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("segredo interno"))));
    }
}