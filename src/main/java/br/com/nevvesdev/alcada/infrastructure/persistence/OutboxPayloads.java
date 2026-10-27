package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.CreditLineApproved;
import br.com.nevvesdev.alcada.domain.model.CreditLineRejected;
import br.com.nevvesdev.alcada.domain.model.DomainEvent;

import java.util.Locale;
import java.util.UUID;

final class OutboxPayloads {

    private OutboxPayloads() {
    }

    static String serialize(UUID eventId, DomainEvent event) {
        return switch (event) {
            case CreditLineApproved approved -> String.format(
                    Locale.ROOT,
                    "{\"eventId\":\"%s\",\"eventType\":\"CreditLineApproved\",\"requestId\":\"%s\","
                            + "\"approvedAmount\":%s,\"currency\":\"BRL\",\"occurredAt\":\"%s\"}",
                    eventId,
                    approved.requestId(),
                    approved.approvedAmount().amount().toPlainString(),
                    approved.occurredAt());
            case CreditLineRejected rejected -> String.format(
                    Locale.ROOT,
                    "{\"eventId\":\"%s\",\"eventType\":\"CreditLineRejected\",\"requestId\":\"%s\","
                            + "\"reason\":\"%s\",\"occurredAt\":\"%s\"}",
                    eventId,
                    rejected.requestId(),
                    rejected.reason().name(),
                    rejected.occurredAt());
        };
    }
}