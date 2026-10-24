package br.com.nevvesdev.alcada.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.APPROVED;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.BUREAU_CHECKED;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.LIMIT_RESERVED;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.PENDING;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.REJECTED;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.RISK_ANALYZED;
import static br.com.nevvesdev.alcada.domain.model.CreditRequestStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;

class CreditRequestStatusTest {

    private static final Map<CreditRequestStatus, Set<CreditRequestStatus>> EXPECTED = Map.of(
            PENDING, EnumSet.of(BUREAU_CHECKED, REJECTED),
            BUREAU_CHECKED, EnumSet.of(RISK_ANALYZED, REJECTED),
            RISK_ANALYZED, EnumSet.of(LIMIT_RESERVED, UNDER_REVIEW, REJECTED),
            UNDER_REVIEW, EnumSet.of(LIMIT_RESERVED, REJECTED),
            LIMIT_RESERVED, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.noneOf(CreditRequestStatus.class),
            REJECTED, EnumSet.noneOf(CreditRequestStatus.class)
    );

    @Test
    void shouldAllowOnlyDeclaredTransitions() {
        for (CreditRequestStatus from : CreditRequestStatus.values()) {
            for (CreditRequestStatus to : CreditRequestStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as(from + " -> " + to)
                        .isEqualTo(EXPECTED.get(from).contains(to));
            }
        }
    }

    @Test
    void shouldCoverEveryStatusInTheExpectedMatrix() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(CreditRequestStatus.values());
    }

    @Test
    void shouldIdentifyTerminalStatuses() {
        for (CreditRequestStatus status : CreditRequestStatus.values()) {
            assertThat(status.isTerminal())
                    .as(status.name())
                    .isEqualTo(status == APPROVED || status == REJECTED);
        }
    }
}