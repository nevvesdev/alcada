package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditRequestTest {

    private static final Instant T0 = Instant.parse("2026-10-24T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(1);
    private static final Instant T2 = T0.plusSeconds(2);
    private static final Instant T3 = T0.plusSeconds(3);
    private static final Instant T4 = T0.plusSeconds(4);

    private static CreditRequest newRequest() {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-123"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                T0);
    }

    private static CreditRequest requestWithDecision(RiskDecision decision) {
        CreditRequest request = newRequest();
        request.registerBureauScore(CreditScore.of(700), T1);
        request.registerRiskDecision(decision, T2);
        return request;
    }

    @Test
    void shouldOpenAsPending() {
        CreditRequest request = newRequest();

        assertThat(request.status()).isEqualTo(CreditRequestStatus.PENDING);
        assertThat(request.createdAt()).isEqualTo(T0);
        assertThat(request.updatedAt()).isEqualTo(T0);
        assertThat(request.creditScore()).isNull();
        assertThat(request.riskDecision()).isNull();
        assertThat(request.rejectionReason()).isNull();
        assertThat(request.isTerminal()).isFalse();
    }

    @Test
    void shouldRejectZeroRequestedAmount() {
        assertThatThrownBy(() -> CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-123"),
                Cpf.of("529.982.247-25"),
                Money.ZERO,
                Money.of("4000"),
                T0)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldFollowHappyPath() {
        CreditRequest request = newRequest();

        request.registerBureauScore(CreditScore.of(700), T1);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.BUREAU_CHECKED);
        assertThat(request.creditScore()).isEqualTo(CreditScore.of(700));

        request.registerRiskDecision(RiskDecision.approve(), T2);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.RISK_ANALYZED);
        assertThat(request.riskDecision()).isEqualTo(RiskDecision.approve());

        request.reserveLimit(T3);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.LIMIT_RESERVED);

        request.approve(T4);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(request.updatedAt()).isEqualTo(T4);
        assertThat(request.createdAt()).isEqualTo(T0);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void shouldGoThroughManualReview() {
        CreditRequest request = requestWithDecision(RiskDecision.manualReview());

        request.sendToReview(T3);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.UNDER_REVIEW);

        request.reserveLimit(T4);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.LIMIT_RESERVED);
    }

    @Test
    void shouldRejectAfterDeniedRiskDecision() {
        CreditRequest request = requestWithDecision(RiskDecision.deny(RejectionReason.LOW_SCORE));

        request.reject(RejectionReason.LOW_SCORE, T3);

        assertThat(request.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEqualTo(RejectionReason.LOW_SCORE);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void shouldRejectFromPendingWhenBureauIsUnavailable() {
        CreditRequest request = newRequest();

        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T1);

        assertThat(request.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEqualTo(RejectionReason.BUREAU_UNAVAILABLE);
    }

    @Test
    void shouldAllowRejectionAfterLimitReservation() {
        CreditRequest request = requestWithDecision(RiskDecision.approve());
        request.reserveLimit(T3);

        request.reject(RejectionReason.APPROVAL_FAILED, T4);

        assertThat(request.status()).isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEqualTo(RejectionReason.APPROVAL_FAILED);
    }

    @Test
    void shouldNotReserveLimitWhenVerdictRequiresReview() {
        CreditRequest request = requestWithDecision(RiskDecision.manualReview());

        assertThatThrownBy(() -> request.reserveLimit(T3))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.RISK_ANALYZED);
    }

    @Test
    void shouldNotReserveLimitWhenVerdictIsDenied() {
        CreditRequest request = requestWithDecision(RiskDecision.deny(RejectionReason.LOW_SCORE));

        assertThatThrownBy(() -> request.reserveLimit(T3))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void shouldNotSendToReviewWhenVerdictIsApproved() {
        CreditRequest request = requestWithDecision(RiskDecision.approve());

        assertThatThrownBy(() -> request.sendToReview(T3))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.RISK_ANALYZED);
    }

    @Test
    void shouldNotSkipSteps() {
        CreditRequest request = newRequest();

        assertThatThrownBy(() -> request.registerRiskDecision(RiskDecision.approve(), T1))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> request.reserveLimit(T1))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> request.approve(T1))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.PENDING);
        assertThat(request.updatedAt()).isEqualTo(T0);
    }

    @Test
    void shouldNotAllowTransitionsFromApproved() {
        CreditRequest request = requestWithDecision(RiskDecision.approve());
        request.reserveLimit(T3);
        request.approve(T4);

        assertThatThrownBy(() -> request.reject(RejectionReason.APPROVAL_FAILED, T4))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(request.rejectionReason()).isNull();
    }

    @Test
    void shouldNotAllowTransitionsFromRejected() {
        CreditRequest request = newRequest();
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T1);

        assertThatThrownBy(() -> request.registerBureauScore(CreditScore.of(700), T2))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> request.reject(RejectionReason.LOW_SCORE, T2))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.rejectionReason()).isEqualTo(RejectionReason.BUREAU_UNAVAILABLE);
    }

    @Test
    void shouldNotStoreScoreWhenTransitionFails() {
        CreditRequest request = newRequest();
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T1);

        assertThatThrownBy(() -> request.registerBureauScore(CreditScore.of(700), T2))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.creditScore()).isNull();
    }

    @Test
    void shouldBeIdentifiedOnlyById() {
        CreditRequestId id = new CreditRequestId(UUID.randomUUID());
        CreditRequest first = CreditRequest.open(
                id, IdempotencyKey.of("key-1"), Cpf.of("529.982.247-25"), Money.of("100"), Money.of("1000"), T0);
        CreditRequest second = CreditRequest.open(
                id, IdempotencyKey.of("key-2"), Cpf.of("111.444.777-35"), Money.of("200"), Money.of("2000"), T1);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(newRequest());
    }

    @Test
    void shouldNotExposeFullCpfInToString() {
        assertThat(newRequest().toString()).doesNotContain("52998224725");
    }
}