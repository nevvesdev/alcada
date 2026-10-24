package br.com.nevvesdev.alcada.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum CreditRequestStatus {
    PENDING,
    BUREAU_CHECKED,
    RISK_ANALYZED,
    UNDER_REVIEW,
    LIMIT_RESERVED,
    APPROVED,
    REJECTED;

    public boolean canTransitionTo(CreditRequestStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    private Set<CreditRequestStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(BUREAU_CHECKED, REJECTED);
            case BUREAU_CHECKED -> EnumSet.of(RISK_ANALYZED, REJECTED);
            case RISK_ANALYZED -> EnumSet.of(LIMIT_RESERVED, UNDER_REVIEW, REJECTED);
            case UNDER_REVIEW -> EnumSet.of(LIMIT_RESERVED, REJECTED);
            case LIMIT_RESERVED -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> EnumSet.noneOf(CreditRequestStatus.class);
        };
    }
}