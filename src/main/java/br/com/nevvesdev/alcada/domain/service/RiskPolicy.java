package br.com.nevvesdev.alcada.domain.service;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;

import java.math.BigDecimal;

public final class RiskPolicy {

    private final CreditScore minimumScore;
    private final CreditScore automaticApprovalScore;
    private final BigDecimal incomeMultiplier;

    public RiskPolicy(CreditScore minimumScore, CreditScore automaticApprovalScore, BigDecimal incomeMultiplier) {
        if (minimumScore == null || automaticApprovalScore == null || incomeMultiplier == null) {
            throw new DomainValidationException("Parâmetros da política de risco são obrigatórios");
        }
        if (automaticApprovalScore.isLowerThan(minimumScore)) {
            throw new DomainValidationException(
                    "Score de aprovação automática não pode ser menor que o score mínimo");
        }
        if (incomeMultiplier.signum() <= 0) {
            throw new DomainValidationException("Multiplicador de renda deve ser maior que zero");
        }
        this.minimumScore = minimumScore;
        this.automaticApprovalScore = automaticApprovalScore;
        this.incomeMultiplier = incomeMultiplier;
    }

    public static RiskPolicy standard() {
        return new RiskPolicy(CreditScore.of(400), CreditScore.of(650), BigDecimal.valueOf(5));
    }

    public RiskDecision evaluate(CreditScore score, Money requestedAmount, Money declaredIncome) {
        if (score == null || requestedAmount == null || declaredIncome == null) {
            throw new DomainValidationException("Dados para análise de risco são obrigatórios");
        }
        if (score.isLowerThan(minimumScore)) {
            return RiskDecision.deny(RejectionReason.LOW_SCORE);
        }
        if (requestedAmount.isGreaterThan(declaredIncome.multiply(incomeMultiplier))) {
            return RiskDecision.deny(RejectionReason.INCOME_INCOMPATIBLE);
        }
        if (score.isLowerThan(automaticApprovalScore)) {
            return RiskDecision.manualReview();
        }
        return RiskDecision.approve();
    }
}