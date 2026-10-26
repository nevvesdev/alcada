package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditRequestRestoreTest {

    private static final CreditRequestId ID = CreditRequestId.generate();
    private static final IdempotencyKey KEY = IdempotencyKey.of("key-1");
    private static final Cpf CPF = Cpf.of("529.982.247-25");
    private static final Money REQUESTED = Money.of("5000");
    private static final Money INCOME = Money.of("4000");
    private static final CreditScore SCORE = CreditScore.of(700);
    private static final Instant T0 = Instant.parse("2026-10-26T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(1);

    private static CreditRequest restore(CreditRequestStatus status,
                                         CreditScore score,
                                         RiskDecision decision,
                                         RejectionReason reason,
                                         Instant updatedAt) {
        return CreditRequest.restore(ID, KEY, CPF, REQUESTED, INCOME, status, score, decision, reason, T0, updatedAt);
    }

    @Test
    void shouldRestorePendingRequest() {
        CreditRequest request = restore(CreditRequestStatus.PENDING, null, null, null, T0);

        assertThat(request.id()).isEqualTo(ID);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.PENDING);
        assertThat(request.createdAt()).isEqualTo(T0);
        assertThat(request.updatedAt()).isEqualTo(T0);
    }

    @Test
    void shouldRestoreApprovedRequest() {
        CreditRequest request = restore(CreditRequestStatus.APPROVED, SCORE, RiskDecision.approve(), null, T1);

        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(request.creditScore()).isEqualTo(SCORE);
        assertThat(request.riskDecision()).isEqualTo(RiskDecision.approve());
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void shouldRestoreRejectedRequestWithoutScoreAndDecision() {
        CreditRequest request = restore(
                CreditRequestStatus.REJECTED, null, null, RejectionReason.BUREAU_UNAVAILABLE, T1);

        assertThat(request.rejectionReason()).isEqualTo(RejectionReason.BUREAU_UNAVAILABLE);
        assertThat(request.creditScore()).isNull();
    }

    @Test
    void shouldRestoreRejectedRequestWithDeniedDecision() {
        CreditRequest request = restore(
                CreditRequestStatus.REJECTED,
                SCORE,
                RiskDecision.deny(RejectionReason.LOW_SCORE),
                RejectionReason.LOW_SCORE,
                T1);

        assertThat(request.riskDecision().isDenied()).isTrue();
    }

    @Test
    void shouldContinueSagaAfterRestore() {
        CreditRequest request = restore(CreditRequestStatus.BUREAU_CHECKED, SCORE, null, null, T1);

        request.registerRiskDecision(RiskDecision.approve(), T1.plusSeconds(1));
        request.reserveLimit(T1.plusSeconds(2));
        request.approve(T1.plusSeconds(3));

        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
    }

    @Test
    void shouldRejectPendingRequestWithScore() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.PENDING, SCORE, null, null, T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectPendingRequestWithDecision() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.PENDING, null, RiskDecision.approve(), null, T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectBureauCheckedRequestWithoutScore() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.BUREAU_CHECKED, null, null, null, T1))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectRiskAnalyzedRequestWithoutDecision() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.RISK_ANALYZED, SCORE, null, null, T1))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectRejectedRequestWithoutReason() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.REJECTED, null, null, null, T1))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectApprovedRequestWithRejectionReason() {
        assertThatThrownBy(() -> restore(
                CreditRequestStatus.APPROVED, SCORE, RiskDecision.approve(), RejectionReason.LOW_SCORE, T1))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectUpdateDateBeforeCreationDate() {
        assertThatThrownBy(() -> restore(CreditRequestStatus.PENDING, null, null, null, T0.minusSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
    }
}