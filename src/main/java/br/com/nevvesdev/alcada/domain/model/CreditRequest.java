package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Objects;

public final class CreditRequest {

    private final CreditRequestId id;
    private final IdempotencyKey idempotencyKey;
    private final Cpf cpf;
    private final Money requestedAmount;
    private final Money declaredIncome;
    private final Instant createdAt;
    private CreditRequestStatus status;
    private CreditScore creditScore;
    private RiskDecision riskDecision;
    private RejectionReason rejectionReason;
    private Instant updatedAt;

    private CreditRequest(CreditRequestId id,
                          IdempotencyKey idempotencyKey,
                          Cpf cpf,
                          Money requestedAmount,
                          Money declaredIncome,
                          Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.cpf = cpf;
        this.requestedAmount = requestedAmount;
        this.declaredIncome = declaredIncome;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = CreditRequestStatus.PENDING;
    }

    public static CreditRequest open(CreditRequestId id,
                                     IdempotencyKey idempotencyKey,
                                     Cpf cpf,
                                     Money requestedAmount,
                                     Money declaredIncome,
                                     Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(declaredIncome, "declaredIncome");
        Objects.requireNonNull(now, "now");
        if (requestedAmount.isZero()) {
            throw new DomainValidationException("Valor solicitado deve ser maior que zero");
        }
        return new CreditRequest(id, idempotencyKey, cpf, requestedAmount, declaredIncome, now);
    }

    public void registerBureauScore(CreditScore score, Instant now) {
        Objects.requireNonNull(score, "score");
        transitionTo(CreditRequestStatus.BUREAU_CHECKED, now);
        this.creditScore = score;
    }

    public void registerRiskDecision(RiskDecision decision, Instant now) {
        Objects.requireNonNull(decision, "decision");
        transitionTo(CreditRequestStatus.RISK_ANALYZED, now);
        this.riskDecision = decision;
    }

    public void sendToReview(Instant now) {
        ensureCanTransitionTo(CreditRequestStatus.UNDER_REVIEW);
        requireManualReviewVerdict();
        transitionTo(CreditRequestStatus.UNDER_REVIEW, now);
    }

    public void reserveLimit(Instant now) {
        ensureCanTransitionTo(CreditRequestStatus.LIMIT_RESERVED);
        if (status == CreditRequestStatus.RISK_ANALYZED) {
            requireApprovedVerdict();
        }
        transitionTo(CreditRequestStatus.LIMIT_RESERVED, now);
    }

    public void approve(Instant now) {
        transitionTo(CreditRequestStatus.APPROVED, now);
    }

    public void reject(RejectionReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        transitionTo(CreditRequestStatus.REJECTED, now);
        this.rejectionReason = reason;
    }

    public CreditRequestId id() {
        return id;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public Cpf cpf() {
        return cpf;
    }

    public Money requestedAmount() {
        return requestedAmount;
    }

    public Money declaredIncome() {
        return declaredIncome;
    }

    public CreditRequestStatus status() {
        return status;
    }

    public CreditScore creditScore() {
        return creditScore;
    }

    public RiskDecision riskDecision() {
        return riskDecision;
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    private void transitionTo(CreditRequestStatus target, Instant now) {
        Objects.requireNonNull(now, "now");
        ensureCanTransitionTo(target);
        this.status = target;
        this.updatedAt = now;
    }

    private void ensureCanTransitionTo(CreditRequestStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Transição inválida de " + status + " para " + target);
        }
    }

    private void requireApprovedVerdict() {
        if (riskDecision == null || !riskDecision.isApproved()) {
            throw new InvalidStateTransitionException("Decisão de risco não permite reservar limite");
        }
    }

    private void requireManualReviewVerdict() {
        if (riskDecision == null || !riskDecision.requiresManualReview()) {
            throw new InvalidStateTransitionException("Decisão de risco não exige revisão manual");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CreditRequest that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "CreditRequest[id=" + id + ", status=" + status + "]";
    }
}