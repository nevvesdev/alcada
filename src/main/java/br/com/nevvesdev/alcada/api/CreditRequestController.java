package br.com.nevvesdev.alcada.api;

import br.com.nevvesdev.alcada.application.usecase.GetCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestCommand;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestResult;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ProcessCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ResolveManualReviewUseCase;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-requests")
@Tag(name = "Solicitações de crédito")
public class CreditRequestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreditRequestController.class);

    private final OpenCreditRequestUseCase openUseCase;
    private final ProcessCreditRequestUseCase processUseCase;
    private final GetCreditRequestUseCase getUseCase;
    private final ResolveManualReviewUseCase reviewUseCase;

    public CreditRequestController(OpenCreditRequestUseCase openUseCase,
                                   ProcessCreditRequestUseCase processUseCase,
                                   GetCreditRequestUseCase getUseCase,
                                   ResolveManualReviewUseCase reviewUseCase) {
        this.openUseCase = openUseCase;
        this.processUseCase = processUseCase;
        this.getUseCase = getUseCase;
        this.reviewUseCase = reviewUseCase;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abre uma solicitação de limite de crédito e executa a saga")
    public ResponseEntity<CreditRequestResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateCreditRequestRequest body) {
        OpenCreditRequestResult opened = openUseCase.execute(new OpenCreditRequestCommand(
                IdempotencyKey.of(idempotencyKey),
                Cpf.of(body.cpf()),
                Money.of(body.requestedAmount()),
                Money.of(body.declaredIncome())));
        if (!opened.newlyCreated()) {
            return ResponseEntity.ok(CreditRequestResponse.from(opened.creditRequest()));
        }
        CreditRequest processed = process(opened.creditRequest().id());
        HttpStatus status = isSettled(processed) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(processed.id().value())
                .toUri();
        return ResponseEntity.status(status).location(location).body(CreditRequestResponse.from(processed));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma solicitação de crédito")
    public CreditRequestResponse get(@PathVariable UUID id) {
        return CreditRequestResponse.from(getUseCase.execute(new CreditRequestId(id)));
    }

    @PostMapping("/{id}/review/approve")
    @Operation(summary = "Aprova uma solicitação em revisão manual")
    public CreditRequestResponse approveReview(@PathVariable UUID id) {
        return CreditRequestResponse.from(reviewUseCase.approve(new CreditRequestId(id)));
    }

    @PostMapping("/{id}/review/deny")
    @Operation(summary = "Nega uma solicitação em revisão manual")
    public CreditRequestResponse denyReview(@PathVariable UUID id) {
        return CreditRequestResponse.from(reviewUseCase.deny(new CreditRequestId(id)));
    }

    private CreditRequest process(CreditRequestId id) {
        try {
            return processUseCase.execute(id);
        } catch (RuntimeException exception) {
            LOGGER.warn("Saga interrompida para a solicitação {}; a recuperação irá retomá-la", id, exception);
            return getUseCase.execute(id);
        }
    }

    private static boolean isSettled(CreditRequest request) {
        return request.isTerminal() || request.status() == CreditRequestStatus.UNDER_REVIEW;
    }
}