package br.com.nevvesdev.alcada.api;

import br.com.nevvesdev.alcada.domain.model.CreditRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditRequestResponse(UUID id,
                                    String status,
                                    BigDecimal requestedAmount,
                                    BigDecimal declaredIncome,
                                    String cpf,
                                    Integer creditScore,
                                    String riskVerdict,
                                    String rejectionReason,
                                    Instant createdAt,
                                    Instant updatedAt) {

    public static CreditRequestResponse from(CreditRequest request) {
        return new CreditRequestResponse(
                request.id().value(),
                request.status().name(),
                request.requestedAmount().amount(),
                request.declaredIncome().amount(),
                request.cpf().masked(),
                request.creditScore() == null ? null : request.creditScore().value(),
                request.riskDecision() == null ? null : request.riskDecision().verdict().name(),
                request.rejectionReason() == null ? null : request.rejectionReason().name(),
                request.createdAt(),
                request.updatedAt());
    }
}