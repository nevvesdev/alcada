package br.com.nevvesdev.alcada.domain.service;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;
import br.com.nevvesdev.alcada.domain.model.RiskVerdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskPolicyTest {

    private final RiskPolicy policy = RiskPolicy.standard();

    @Test
    void shouldDenyWhenScoreIsBelowMinimum() {
        RiskDecision decision = policy.evaluate(CreditScore.of(399), Money.of("1000"), Money.of("5000"));

        assertThat(decision.verdict()).isEqualTo(RiskVerdict.DENIED);
        assertThat(decision.rejectionReason()).isEqualTo(RejectionReason.LOW_SCORE);
    }

    @Test
    void shouldPrioritizeLowScoreOverIncomeCompatibility() {
        RiskDecision decision = policy.evaluate(CreditScore.of(100), Money.of("999999"), Money.of("1000"));

        assertThat(decision.rejectionReason()).isEqualTo(RejectionReason.LOW_SCORE);
    }

    @Test
    void shouldDenyWhenRequestedAmountExceedsIncomeCap() {
        RiskDecision decision = policy.evaluate(CreditScore.of(800), Money.of("5000.01"), Money.of("1000"));

        assertThat(decision.verdict()).isEqualTo(RiskVerdict.DENIED);
        assertThat(decision.rejectionReason()).isEqualTo(RejectionReason.INCOME_INCOMPATIBLE);
    }

    @Test
    void shouldAcceptRequestedAmountEqualToIncomeCap() {
        RiskDecision decision = policy.evaluate(CreditScore.of(800), Money.of("5000.00"), Money.of("1000"));

        assertThat(decision.isApproved()).isTrue();
    }

    @Test
    void shouldDenyWhenIncomeIsZero() {
        RiskDecision decision = policy.evaluate(CreditScore.of(800), Money.of("1"), Money.ZERO);

        assertThat(decision.rejectionReason()).isEqualTo(RejectionReason.INCOME_INCOMPATIBLE);
    }

    @Test
    void shouldSendToManualReviewAtMinimumScore() {
        RiskDecision decision = policy.evaluate(CreditScore.of(400), Money.of("1000"), Money.of("5000"));

        assertThat(decision.requiresManualReview()).isTrue();
    }

    @Test
    void shouldSendToManualReviewJustBelowAutomaticApproval() {
        RiskDecision decision = policy.evaluate(CreditScore.of(649), Money.of("1000"), Money.of("5000"));

        assertThat(decision.requiresManualReview()).isTrue();
    }

    @Test
    void shouldApproveAtAutomaticApprovalScore() {
        RiskDecision decision = policy.evaluate(CreditScore.of(650), Money.of("1000"), Money.of("5000"));

        assertThat(decision.isApproved()).isTrue();
        assertThat(decision.rejectionReason()).isNull();
    }

    @Test
    void shouldRejectAutomaticApprovalScoreBelowMinimum() {
        assertThatThrownBy(() -> new RiskPolicy(CreditScore.of(600), CreditScore.of(500), BigDecimal.ONE))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectNonPositiveMultiplier() {
        assertThatThrownBy(() -> new RiskPolicy(CreditScore.of(400), CreditScore.of(650), BigDecimal.ZERO))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectNullEvaluationData() {
        assertThatThrownBy(() -> policy.evaluate(null, Money.of("1"), Money.of("1")))
                .isInstanceOf(DomainValidationException.class);
    }
}