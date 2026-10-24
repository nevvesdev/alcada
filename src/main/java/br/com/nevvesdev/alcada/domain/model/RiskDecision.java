package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

import java.util.Objects;

public record RiskDecision(RiskVerdict verdict, RejectionReason rejectionReason) {

    public RiskDecision {
        Objects.requireNonNull(verdict, "verdict");
        if (verdict == RiskVerdict.DENIED && rejectionReason == null) {
            throw new DomainValidationException("Decisão negada exige um motivo");
        }
        if (verdict != RiskVerdict.DENIED && rejectionReason != null) {
            throw new DomainValidationException("Motivo só é permitido em decisão negada");
        }
    }

    public static RiskDecision approve() {
        return new RiskDecision(RiskVerdict.APPROVED, null);
    }

    public static RiskDecision manualReview() {
        return new RiskDecision(RiskVerdict.MANUAL_REVIEW, null);
    }

    public static RiskDecision deny(RejectionReason reason) {
        return new RiskDecision(RiskVerdict.DENIED, reason);
    }

    public boolean isApproved() {
        return verdict == RiskVerdict.APPROVED;
    }

    public boolean requiresManualReview() {
        return verdict == RiskVerdict.MANUAL_REVIEW;
    }

    public boolean isDenied() {
        return verdict == RiskVerdict.DENIED;
    }
}