package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.DomainEvent;

import java.util.UUID;

final class OutboxEventMapper {

    static final String AGGREGATE_TYPE = "CreditRequest";

    private OutboxEventMapper() {
    }

    static OutboxEventJpaEntity toEntity(DomainEvent event) {
        UUID eventId = UUID.randomUUID();
        return new OutboxEventJpaEntity(
                eventId,
                AGGREGATE_TYPE,
                event.requestId().value(),
                event.getClass().getSimpleName(),
                OutboxPayloads.serialize(eventId, event),
                event.occurredAt());
    }
}