package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.domain.model.CreditLineApproved;
import br.com.nevvesdev.alcada.domain.model.CreditLineRejected;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPayloadsTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final CreditRequestId REQUEST_ID =
            new CreditRequestId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void shouldSerializeApprovedEvent() {
        String payload = OutboxPayloads.serialize(
                EVENT_ID, new CreditLineApproved(REQUEST_ID, Money.of("5000.50"), OCCURRED_AT));

        assertThat(payload).isEqualTo(
                "{\"eventId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"eventType\":\"CreditLineApproved\","
                        + "\"requestId\":\"00000000-0000-0000-0000-000000000002\","
                        + "\"approvedAmount\":5000.50,\"currency\":\"BRL\","
                        + "\"occurredAt\":\"2026-10-27T12:00:00Z\"}");
    }

    @Test
    void shouldSerializeRejectedEvent() {
        String payload = OutboxPayloads.serialize(
                EVENT_ID, new CreditLineRejected(REQUEST_ID, RejectionReason.LOW_SCORE, OCCURRED_AT));

        assertThat(payload).isEqualTo(
                "{\"eventId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"eventType\":\"CreditLineRejected\","
                        + "\"requestId\":\"00000000-0000-0000-0000-000000000002\","
                        + "\"reason\":\"LOW_SCORE\","
                        + "\"occurredAt\":\"2026-10-27T12:00:00Z\"}");
    }

    @Test
    void shouldNotExposePersonalData() {
        String payload = OutboxPayloads.serialize(
                EVENT_ID, new CreditLineApproved(REQUEST_ID, Money.of("100"), OCCURRED_AT));

        assertThat(payload).doesNotContain("cpf");
    }
}