package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;
import br.com.nevvesdev.alcada.domain.model.RiskVerdict;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreditRequestMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-26T12:00:00Z");

    private static CreditRequest pending() {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-1"),
                Cpf.of("529.982.247-25"),
                Money.of("5000.50"),
                Money.of("4000"),
                T0);
    }

    private static void assertRoundTrip(CreditRequest original) {
        CreditRequest restored = CreditRequestMapper.toDomain(CreditRequestMapper.toEntity(original));

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldRoundTripPendingRequest() {
        assertRoundTrip(pending());
    }

    @Test
    void shouldRoundTripApprovedRequest() {
        CreditRequest request = pending();
        request.registerBureauScore(CreditScore.of(700), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.approve(), T0.plusSeconds(2));
        request.reserveLimit(T0.plusSeconds(3));
        request.approve(T0.plusSeconds(4));

        assertRoundTrip(request);
    }

    @Test
    void shouldRoundTripRequestUnderManualReview() {
        CreditRequest request = pending();
        request.registerBureauScore(CreditScore.of(500), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.manualReview(), T0.plusSeconds(2));
        request.sendToReview(T0.plusSeconds(3));

        assertRoundTrip(request);
    }

    @Test
    void shouldRoundTripRejectedRequestWithDeniedDecision() {
        CreditRequest request = pending();
        request.registerBureauScore(CreditScore.of(300), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.deny(RejectionReason.LOW_SCORE), T0.plusSeconds(2));
        request.reject(RejectionReason.LOW_SCORE, T0.plusSeconds(3));

        assertRoundTrip(request);
    }

    @Test
    void shouldRoundTripRejectedRequestWithoutScore() {
        CreditRequest request = pending();
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));

        assertRoundTrip(request);
    }

    @Test
    void shouldMapAggregateFieldsToEntity() {
        CreditRequest request = pending();
        request.registerBureauScore(CreditScore.of(500), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.manualReview(), T0.plusSeconds(2));

        CreditRequestJpaEntity entity = CreditRequestMapper.toEntity(request);

        assertThat(entity.getId()).isEqualTo(request.id().value());
        assertThat(entity.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(entity.getCpf()).isEqualTo("52998224725");
        assertThat(entity.getRequestedAmount()).hasToString("5000.50");
        assertThat(entity.getStatus()).isEqualTo(CreditRequestStatus.RISK_ANALYZED);
        assertThat(entity.getCreditScore()).isEqualTo(500);
        assertThat(entity.getRiskVerdict()).isEqualTo(RiskVerdict.MANUAL_REVIEW);
        assertThat(entity.getRiskRejectionReason()).isNull();
        assertThat(entity.getRejectionReason()).isNull();
    }
}