package br.com.nevvesdev.alcada.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(UUID id,
                            String aggregateType,
                            UUID aggregateId,
                            String eventType,
                            String payload,
                            Instant occurredAt) {
}