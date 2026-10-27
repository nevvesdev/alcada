package br.com.nevvesdev.alcada.domain.model;

import java.time.Instant;
import java.util.Objects;

public record CreditLineRejected(CreditRequestId requestId, RejectionReason reason, Instant occurredAt)
        implements DomainEvent {

    public CreditLineRejected {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}