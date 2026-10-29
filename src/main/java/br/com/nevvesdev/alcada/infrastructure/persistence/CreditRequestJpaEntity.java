package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_request")
public class CreditRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Convert(converter = CpfAttributeConverter.class)
    @Column(name = "cpf", nullable = false, length = 128)
    private String cpf;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "declared_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredIncome;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CreditRequestStatus status;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_verdict", length = 32)
    private RiskVerdict riskVerdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rejection_reason", length = 64)
    private RejectionReason riskRejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 64)
    private RejectionReason rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditRequestJpaEntity() {
    }

    public CreditRequestJpaEntity(UUID id,
                                  String idempotencyKey,
                                  String cpf,
                                  BigDecimal requestedAmount,
                                  BigDecimal declaredIncome,
                                  CreditRequestStatus status,
                                  Integer creditScore,
                                  RiskVerdict riskVerdict,
                                  RejectionReason riskRejectionReason,
                                  RejectionReason rejectionReason,
                                  Instant createdAt,
                                  Instant updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.cpf = cpf;
        this.requestedAmount = requestedAmount;
        this.declaredIncome = declaredIncome;
        this.status = status;
        this.creditScore = creditScore;
        this.riskVerdict = riskVerdict;
        this.riskRejectionReason = riskRejectionReason;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCpf() {
        return cpf;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getDeclaredIncome() {
        return declaredIncome;
    }

    public CreditRequestStatus getStatus() {
        return status;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public RiskVerdict getRiskVerdict() {
        return riskVerdict;
    }

    public RejectionReason getRiskRejectionReason() {
        return riskRejectionReason;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}