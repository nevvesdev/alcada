package br.com.nevvesdev.alcada.domain.model;

import java.time.Instant;
import java.util.Objects;

public record CreditLineApproved(CreditRequestId requestId, Money approvedAmount, Instant occurredAt)
        implements DomainEvent {

    public CreditLineApproved {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(approvedAmount, "approvedAmount");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}