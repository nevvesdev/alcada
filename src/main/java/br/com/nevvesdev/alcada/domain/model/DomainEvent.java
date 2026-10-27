package br.com.nevvesdev.alcada.domain.model;

import java.time.Instant;

public sealed interface DomainEvent permits CreditLineApproved, CreditLineRejected {

    CreditRequestId requestId();

    Instant occurredAt();
}