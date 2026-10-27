package br.com.nevvesdev.alcada.infrastructure.outbox;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.infrastructure.persistence.OutboxEventJpaEntity;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxRelayTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC);

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final RecordingPublisher publisher = new RecordingPublisher();

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
    }

    private OutboxRelay relay(int batchSize, int maxAttempts) {
        return new OutboxRelay(outbox, publisher, transactionManager, CLOCK, batchSize, maxAttempts);
    }

    private OutboxEventJpaEntity saveEvent(Instant occurredAt) {
        return outbox.save(new OutboxEventJpaEntity(
                UUID.randomUUID(), "CreditRequest", UUID.randomUUID(), "CreditLineApproved", "{}", occurredAt));
    }

    @Test
    void shouldPublishPendingEventsInOccurrenceOrderAndMarkThem() {
        OutboxEventJpaEntity third = saveEvent(T0.plusSeconds(3));
        OutboxEventJpaEntity first = saveEvent(T0.plusSeconds(1));
        OutboxEventJpaEntity second = saveEvent(T0.plusSeconds(2));
        OutboxRelay relay = relay(10, 5);

        int published = relay.publishPending();

        assertThat(published).isEqualTo(3);
        assertThat(publisher.published()).extracting(OutboxMessage::id)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(outbox.findAll()).allSatisfy(event -> {
            assertThat(event.getPublishedAt()).isEqualTo(CLOCK.instant());
            assertThat(event.getAttempts()).isZero();
        });
        assertThat(relay.publishPending()).isZero();
        assertThat(publisher.published()).hasSize(3);
    }

    @Test
    void shouldRespectBatchSize() {
        saveEvent(T0.plusSeconds(1));
        saveEvent(T0.plusSeconds(2));
        saveEvent(T0.plusSeconds(3));
        OutboxRelay relay = relay(2, 5);

        assertThat(relay.publishPending()).isEqualTo(2);
        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(relay.publishPending()).isZero();
    }

    @Test
    void shouldKeepFailedEventPendingAndRecordTheError() {
        OutboxEventJpaEntity event = saveEvent(T0.plusSeconds(1));
        publisher.failWhen(message -> true);
        OutboxRelay relay = relay(10, 5);

        assertThat(relay.publishPending()).isZero();

        OutboxEventJpaEntity failed = outbox.findById(event.getId()).orElseThrow();
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("broker fora");

        publisher.failWhen(message -> false);
        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(outbox.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void shouldContinueWhenOneMessageFails() {
        OutboxEventJpaEntity broken = saveEvent(T0.plusSeconds(1));
        OutboxEventJpaEntity healthy = saveEvent(T0.plusSeconds(2));
        publisher.failWhen(message -> message.id().equals(broken.getId()));

        int published = relay(10, 5).publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(outbox.findById(healthy.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(outbox.findById(broken.getId()).orElseThrow().getPublishedAt()).isNull();
    }

    @Test
    void shouldStopRetryingAfterMaxAttempts() {
        OutboxEventJpaEntity event = saveEvent(T0.plusSeconds(1));
        publisher.failWhen(message -> true);
        OutboxRelay relay = relay(10, 2);

        relay.publishPending();
        relay.publishPending();
        relay.publishPending();

        assertThat(publisher.attempts()).isEqualTo(2);
        OutboxEventJpaEntity stuck = outbox.findById(event.getId()).orElseThrow();
        assertThat(stuck.getAttempts()).isEqualTo(2);
        assertThat(stuck.getPublishedAt()).isNull();
    }

    @Test
    void shouldSkipEventsLockedByAnotherTransaction() {
        saveEvent(T0.plusSeconds(1));
        saveEvent(T0.plusSeconds(2));
        OutboxRelay relay = relay(10, 5);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outbox.lockPending(10, 5);
            int publishedByOther = CompletableFuture.supplyAsync(relay::publishPending).join();
            assertThat(publishedByOther).isZero();
        });

        assertThat(relay.publishPending()).isEqualTo(2);
    }

    private static final class RecordingPublisher implements OutboxMessagePublisher {

        private final List<OutboxMessage> published = new CopyOnWriteArrayList<>();
        private volatile int attempts;
        private volatile Predicate<OutboxMessage> failWhen = message -> false;

        void failWhen(Predicate<OutboxMessage> condition) {
            this.failWhen = condition;
        }

        List<OutboxMessage> published() {
            return published;
        }

        int attempts() {
            return attempts;
        }

        @Override
        public void publish(OutboxMessage message) {
            attempts++;
            if (failWhen.test(message)) {
                throw new IllegalStateException("broker fora");
            }
            published.add(message);
        }
    }
}