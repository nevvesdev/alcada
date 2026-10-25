package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.support.FakeCreditBureau;
import br.com.nevvesdev.alcada.application.support.FakeLimitReservation;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolveManualReviewUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final FakeCreditBureau bureau = new FakeCreditBureau();
    private final FakeLimitReservation limits = new FakeLimitReservation();
    private final CreditSagaOrchestrator orchestrator =
            new CreditSagaOrchestrator(repository, bureau, limits, RiskPolicy.standard(), CLOCK);
    private final ResolveManualReviewUseCase useCase = new ResolveManualReviewUseCase(repository, orchestrator);

    private CreditRequest newRequest() {
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-1"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                CLOCK.instant());
        repository.save(request);
        return request;
    }

    private CreditRequest requestUnderReview() {
        bureau.returning(500);
        CreditRequest request = newRequest();
        orchestrator.run(request);
        return request;
    }

    @Test
    void shouldApproveRequestUnderReview() {
        CreditRequest request = requestUnderReview();

        CreditRequest result = useCase.approve(request.id());

        assertThat(result.status()).isEqualTo(CreditRequestStatus.APPROVED);
    }

    @Test
    void shouldDenyRequestUnderReview() {
        CreditRequest request = requestUnderReview();

        CreditRequest result = useCase.deny(request.id());

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.REVIEW_DENIED);
    }

    @Test
    void shouldNotResolveRequestThatIsNotUnderReview() {
        CreditRequest request = newRequest();

        assertThatThrownBy(() -> useCase.approve(request.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> useCase.deny(request.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void shouldFailWhenRequestDoesNotExist() {
        assertThatThrownBy(() -> useCase.approve(CreditRequestId.generate()))
                .isInstanceOf(CreditRequestNotFoundException.class);
        assertThatThrownBy(() -> useCase.deny(CreditRequestId.generate()))
                .isInstanceOf(CreditRequestNotFoundException.class);
    }
}