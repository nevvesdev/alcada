package br.com.nevvesdev.alcada.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditRequestEventsTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant T4 = T0.plusSeconds(4);

    private static CreditRequest newRequest() {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-1"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                T0);
    }

    private static CreditRequest reservedRequest() {
        CreditRequest request = newRequest();
        request.registerBureauScore(CreditScore.of(700), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.approve(), T0.plusSeconds(2));
        request.reserveLimit(T0.plusSeconds(3));
        return request;
    }

    @Test
    void shouldNotRaiseEventsBeforeTerminalState() {
        CreditRequest request = reservedRequest();

        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void shouldRaiseCreditLineApprovedOnApproval() {
        CreditRequest request = reservedRequest();

        request.approve(T4);

        assertThat(request.domainEvents())
                .containsExactly(new CreditLineApproved(request.id(), Money.of("5000"), T4));
    }

    @Test
    void shouldRaiseCreditLineRejectedOnRejection() {
        CreditRequest request = newRequest();

        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T4);

        assertThat(request.domainEvents())
                .containsExactly(new CreditLineRejected(request.id(), RejectionReason.BUREAU_UNAVAILABLE, T4));
    }

    @Test
    void shouldNotRaiseEventWhenTransitionFails() {
        CreditRequest request = newRequest();

        assertThatThrownBy(() -> request.approve(T4)).isInstanceOf(RuntimeException.class);

        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void shouldClearEvents() {
        CreditRequest request = reservedRequest();
        request.approve(T4);

        request.clearDomainEvents();

        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void shouldExposeImmutableCopyOfEvents() {
        CreditRequest request = reservedRequest();
        request.approve(T4);

        assertThatThrownBy(() -> request.domainEvents()
                .add(new CreditLineRejected(request.id(), RejectionReason.LOW_SCORE, T4)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotRaiseEventsWhenRestoring() {
        CreditRequest request = CreditRequest.restore(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-2"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                CreditRequestStatus.REJECTED,
                null,
                null,
                RejectionReason.BUREAU_UNAVAILABLE,
                T0,
                T4);

        assertThat(request.domainEvents()).isEmpty();
    }
}