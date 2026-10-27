package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaCreditRequestRepositoryOutboxTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");

    @Autowired
    private JpaCreditRequestRepository repository;

    @Autowired
    private SpringDataCreditRequestRepository creditRequests;

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        creditRequests.deleteAll();
    }

    private static CreditRequest newRequest(String key) {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of(key),
                Cpf.of("529.982.247-25"),
                Money.of("5000.50"),
                Money.of("4000"),
                T0);
    }

    private static CreditRequest approvedRequest(String key) {
        CreditRequest request = newRequest(key);
        request.registerBureauScore(CreditScore.of(700), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.approve(), T0.plusSeconds(2));
        request.reserveLimit(T0.plusSeconds(3));
        request.approve(T0.plusSeconds(4));
        return request;
    }

    @Test
    void shouldWriteApprovalEventToOutboxTogetherWithState() {
        CreditRequest request = approvedRequest("key-1");

        repository.save(request);

        List<OutboxEventJpaEntity> events = outbox.findAll();
        assertThat(events).hasSize(1);
        OutboxEventJpaEntity event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("CreditRequest");
        assertThat(event.getAggregateId()).isEqualTo(request.id().value());
        assertThat(event.getEventType()).isEqualTo("CreditLineApproved");
        assertThat(event.getPayload()).contains(request.id().toString(), "\"approvedAmount\":5000.50");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void shouldWriteRejectionEventToOutbox() {
        CreditRequest request = newRequest("key-1");
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));

        repository.save(request);

        List<OutboxEventJpaEntity> events = outbox.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("CreditLineRejected");
        assertThat(events.get(0).getPayload()).contains("BUREAU_UNAVAILABLE");
    }

    @Test
    void shouldNotWriteEventsForNonTerminalRequest() {
        repository.save(newRequest("key-1"));

        assertThat(outbox.count()).isZero();
    }

    @Test
    void shouldNotDuplicateEventsWhenSavingAgain() {
        CreditRequest request = approvedRequest("key-1");

        repository.save(request);
        repository.save(request);

        assertThat(outbox.count()).isEqualTo(1);
        assertThat(creditRequests.count()).isEqualTo(1);
    }

    @Test
    void shouldRollBackOutboxWhenIdempotencyKeyConflicts() {
        repository.save(newRequest("key-1"));
        CreditRequest duplicate = newRequest("key-1");
        duplicate.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(outbox.count()).isZero();
        assertThat(creditRequests.count()).isEqualTo(1);
        assertThat(duplicate.domainEvents()).hasSize(1);
    }
}