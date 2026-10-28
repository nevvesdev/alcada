package br.com.nevvesdev.alcada.api;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.infrastructure.persistence.OutboxEventJpaEntity;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataCreditRequestRepository;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditRequestApiIntegrationTest {

    private static final StubHttpServer REMOTE = new StubHttpServer();
    private static final String BUREAU_PATH = "/bureau/scores";
    private static final String RESERVE_PATH = "/limits/reservations";
    private static final String CONFIRM_REGEX = "/limits/reservations/[^/]+/confirm";
    private static final String RELEASE_REGEX = "/limits/reservations/[^/]+";

    private static final String APPROVED_CPF = "529.982.247-25";
    private static final String LOW_SCORE_CPF = "111.444.777-35";
    private static final String REVIEW_CPF = "123.456.789-09";
    private static final String BUREAU_DOWN_CPF = "935.411.347-80";

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("alcada.bureau.base-url", REMOTE::baseUrl);
        registry.add("alcada.limits.base-url", REMOTE::baseUrl);
        registry.add("alcada.bureau.retry.initial-backoff", () -> "1ms");
        registry.add("alcada.limits.retry.initial-backoff", () -> "1ms");
        registry.add("alcada.bureau.circuit-breaker.sliding-window-size", () -> "1000");
        registry.add("alcada.bureau.circuit-breaker.minimum-number-of-calls", () -> "1000");
        registry.add("alcada.limits.circuit-breaker.sliding-window-size", () -> "1000");
        registry.add("alcada.limits.circuit-breaker.minimum-number-of-calls", () -> "1000");
    }

    @AfterAll
    static void stopRemote() {
        REMOTE.close();
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private SpringDataCreditRequestRepository creditRequests;

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        creditRequests.deleteAll();
        REMOTE.reset();
        REMOTE.on("POST", BUREAU_PATH, (request, call) -> bureauResponse(request.body()));
        REMOTE.respond("POST", RESERVE_PATH, Response.json(201, "{}"));
        REMOTE.respondPattern("POST", CONFIRM_REGEX, Response.empty(200));
        REMOTE.respondPattern("DELETE", RELEASE_REGEX, Response.empty(204));
    }

    private static Response bureauResponse(String body) {
        if (body.contains("11144477735")) {
            return Response.json(200, "{\"score\":300}");
        }
        if (body.contains("12345678909")) {
            return Response.json(200, "{\"score\":500}");
        }
        if (body.contains("93541134780")) {
            return Response.empty(503);
        }
        return Response.json(200, "{\"score\":720}");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> create(String key, String cpf) throws Exception {
        String body = String.format(
                "{\"cpf\":\"%s\",\"requestedAmount\":5000,\"declaredIncome\":4000}", cpf);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/credit-requests"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    @Test
    void shouldApproveRequestEndToEnd() throws Exception {
        HttpResponse<String> response = create("e2e-approved", APPROVED_CPF);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"status\":\"APPROVED\"", "\"cpf\":\"***.982.247-**\"");
        HttpResponse<String> fetched = get(location(response));
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("\"status\":\"APPROVED\"");
        assertThat(REMOTE.countRequests("POST", BUREAU_PATH)).isEqualTo(1);
        assertThat(REMOTE.countRequests("POST", RESERVE_PATH)).isEqualTo(1);
        assertThat(REMOTE.countRequests("POST", CONFIRM_REGEX)).isEqualTo(1);
        List<OutboxEventJpaEntity> events = outbox.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("CreditLineApproved");
    }

    @Test
    void shouldRejectLowScoreWithoutTouchingTheLimitService() throws Exception {
        HttpResponse<String> response = create("e2e-low-score", LOW_SCORE_CPF);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"status\":\"REJECTED\"", "\"rejectionReason\":\"LOW_SCORE\"");
        assertThat(REMOTE.countRequests("POST", RESERVE_PATH)).isZero();
        assertThat(outbox.findAll()).extracting(OutboxEventJpaEntity::getEventType)
                .containsExactly("CreditLineRejected");
    }

    @Test
    void shouldReplayRequestWithoutCallingRemoteServicesAgain() throws Exception {
        HttpResponse<String> first = create("e2e-replay", APPROVED_CPF);
        HttpResponse<String> second = create("e2e-replay", APPROVED_CPF);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(location(first)).endsWith(second.body().split("\"id\":\"")[1].substring(0, 36));
        assertThat(REMOTE.countRequests("POST", BUREAU_PATH)).isEqualTo(1);
        assertThat(creditRequests.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void shouldCompleteManualReviewFlow() throws Exception {
        HttpResponse<String> created = create("e2e-review", REVIEW_CPF);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"status\":\"UNDER_REVIEW\"");
        assertThat(outbox.count()).isZero();

        HttpResponse<String> approved = postWithoutBody(location(created) + "/review/approve");

        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(approved.body()).contains("\"status\":\"APPROVED\"");
        assertThat(outbox.findAll()).extracting(OutboxEventJpaEntity::getEventType)
                .containsExactly("CreditLineApproved");
    }

    @Test
    void shouldRejectWhenBureauStaysUnavailableAfterRetries() throws Exception {
        HttpResponse<String> response = create("e2e-bureau-down", BUREAU_DOWN_CPF);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"rejectionReason\":\"BUREAU_UNAVAILABLE\"");
        assertThat(REMOTE.countRequests("POST", BUREAU_PATH)).isEqualTo(3);
    }

    @Test
    void shouldReleaseReservationWhenConfirmationFails() throws Exception {
        REMOTE.respondPattern("POST", CONFIRM_REGEX, Response.empty(500));

        HttpResponse<String> response = create("e2e-confirm-fails", APPROVED_CPF);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"rejectionReason\":\"APPROVAL_FAILED\"");
        assertThat(REMOTE.countRequests("DELETE", RELEASE_REGEX)).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidPayloadWithProblemDetail() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/credit-requests"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "e2e-invalid")
                .POST(HttpRequest.BodyPublishers.ofString("{\"cpf\":\"\"}"))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
    }

    @Test
    void shouldReturnNotFoundForUnknownRequest() throws Exception {
        HttpResponse<String> response = get(baseUrl() + "/api/v1/credit-requests/" + java.util.UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"CREDIT_REQUEST_NOT_FOUND\"");
    }

    @Test
    void shouldExposeOpenApiDocument() throws Exception {
        HttpResponse<String> response = get(baseUrl() + "/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/v1/credit-requests");
    }
}