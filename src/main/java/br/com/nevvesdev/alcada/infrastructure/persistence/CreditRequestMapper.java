package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;

final class CreditRequestMapper {

    private CreditRequestMapper() {
    }

    static CreditRequestJpaEntity toEntity(CreditRequest request) {
        RiskDecision decision = request.riskDecision();
        return new CreditRequestJpaEntity(
                request.id().value(),
                request.idempotencyKey().value(),
                request.cpf().value(),
                request.requestedAmount().amount(),
                request.declaredIncome().amount(),
                request.status(),
                request.creditScore() == null ? null : request.creditScore().value(),
                decision == null ? null : decision.verdict(),
                decision == null ? null : decision.rejectionReason(),
                request.rejectionReason(),
                request.createdAt(),
                request.updatedAt());
    }

    static CreditRequest toDomain(CreditRequestJpaEntity entity) {
        CreditScore score = entity.getCreditScore() == null ? null : CreditScore.of(entity.getCreditScore());
        RiskDecision decision = entity.getRiskVerdict() == null
                ? null
                : new RiskDecision(entity.getRiskVerdict(), entity.getRiskRejectionReason());
        return CreditRequest.restore(
                new CreditRequestId(entity.getId()),
                IdempotencyKey.of(entity.getIdempotencyKey()),
                Cpf.of(entity.getCpf()),
                Money.of(entity.getRequestedAmount()),
                Money.of(entity.getDeclaredIncome()),
                entity.getStatus(),
                score,
                decision,
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}