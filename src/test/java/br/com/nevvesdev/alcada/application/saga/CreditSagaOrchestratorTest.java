package br.com.nevvesdev.alcada.application.saga;

import br.com.nevvesdev.alcada.application.exception.LimitReleaseException;
import br.com.nevvesdev.alcada.application.support.FakeCreditBureau;
import br.com.nevvesdev.alcada.application.support.FakeLimitReservation;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditSagaOrchestratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final FakeCreditBureau bureau = new FakeCreditBureau();
    private final FakeLimitReservation limits = new FakeLimitReservation();
    private final CreditSagaOrchestrator orchestrator =
            new CreditSagaOrchestrator(repository, bureau, limits, RiskPolicy.standard(), CLOCK);

    private CreditRequest newRequest(String requested, String income) {
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-" + UUID.randomUUID()),
                Cpf.of("529.982.247-25"),
                Money.of(requested),
                Money.of(income),
                CLOCK.instant());
        repository.save(request);
        return request;
    }

    @Test
    void shouldApproveAutomaticallyWhenRiskIsAcceptable() {
        bureau.returning(700);
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(result.creditScore()).isEqualTo(CreditScore.of(700));
        assertThat(limits.reservedCount()).isEqualTo(1);
        assertThat(limits.confirmedCount()).isEqualTo(1);
        assertThat(limits.releasedCount()).isZero();
    }

    @Test
    void shouldRejectWhenScoreIsTooLow() {
        bureau.returning(300);
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.LOW_SCORE);
        assertThat(limits.reservedCount()).isZero();
    }

    @Test
    void shouldRejectWhenRequestedAmountIsIncompatibleWithIncome() {
        bureau.returning(800);
        CreditRequest request = newRequest("50000", "1000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.INCOME_INCOMPATIBLE);
        assertThat(limits.reservedCount()).isZero();
    }

    @Test
    void shouldStopAtManualReview() {
        bureau.returning(500);
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.UNDER_REVIEW);
        assertThat(limits.reservedCount()).isZero();
    }

    @Test
    void shouldRejectWhenBureauIsUnavailable() {
        bureau.unavailable();
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.BUREAU_UNAVAILABLE);
        assertThat(result.creditScore()).isNull();
        assertThat(limits.reservedCount()).isZero();
    }

    @Test
    void shouldRejectWithoutReleaseWhenReservationFails() {
        bureau.returning(700);
        limits.failReserve(true);
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.LIMIT_RESERVATION_FAILED);
        assertThat(limits.confirmedCount()).isZero();
        assertThat(limits.releasedCount()).isZero();
    }

    @Test
    void shouldReleaseReservationWhenConfirmationFails() {
        bureau.returning(700);
        limits.failConfirm(true);
        CreditRequest request = newRequest("5000", "4000");

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.APPROVAL_FAILED);
        assertThat(limits.reservedCount()).isEqualTo(1);
        assertThat(limits.releasedCount()).isEqualTo(1);
    }

    @Test
    void shouldKeepRequestResumableWhenReleaseFails() {
        bureau.returning(700);
        limits.failConfirm(true);
        limits.failRelease(true);
        CreditRequest request = newRequest("5000", "4000");

        assertThatThrownBy(() -> orchestrator.run(request)).isInstanceOf(LimitReleaseException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.LIMIT_RESERVED);

        limits.failRelease(false);
        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.APPROVAL_FAILED);
        assertThat(limits.releasedCount()).isEqualTo(1);
    }

    @Test
    void shouldResumeFromPersistedStatusWithoutRepeatingSteps() {
        CreditRequest request = newRequest("5000", "4000");
        request.registerBureauScore(CreditScore.of(700), CLOCK.instant());
        repository.save(request);

        CreditRequest result = orchestrator.run(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(bureau.calls()).isZero();
    }

    @Test
    void shouldDoNothingWhenRequestIsAlreadyTerminal() {
        bureau.returning(300);
        CreditRequest request = newRequest("5000", "4000");
        orchestrator.run(request);

        orchestrator.run(request);

        assertThat(bureau.calls()).isEqualTo(1);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.REJECTED);
    }

    @Test
    void shouldCompleteSagaWhenManualReviewIsApproved() {
        bureau.returning(500);
        CreditRequest request = newRequest("5000", "4000");
        orchestrator.run(request);

        CreditRequest result = orchestrator.approveReview(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(limits.reservedCount()).isEqualTo(1);
        assertThat(limits.confirmedCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectWhenReservationFailsAfterManualApproval() {
        bureau.returning(500);
        CreditRequest request = newRequest("5000", "4000");
        orchestrator.run(request);
        limits.failReserve(true);

        CreditRequest result = orchestrator.approveReview(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.LIMIT_RESERVATION_FAILED);
    }

    @Test
    void shouldRejectWhenManualReviewIsDenied() {
        bureau.returning(500);
        CreditRequest request = newRequest("5000", "4000");
        orchestrator.run(request);

        CreditRequest result = orchestrator.denyReview(request);

        assertThat(result.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(RejectionReason.REVIEW_DENIED);
        assertThat(limits.reservedCount()).isZero();
    }

    @Test
    void shouldNotResolveReviewWhenRequestIsNotUnderReview() {
        CreditRequest request = newRequest("5000", "4000");

        assertThatThrownBy(() -> orchestrator.approveReview(request))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orchestrator.denyReview(request))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.PENDING);
    }
}