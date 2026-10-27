package br.com.nevvesdev.alcada.infrastructure.outbox;

import br.com.nevvesdev.alcada.infrastructure.persistence.OutboxEventJpaEntity;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class OutboxRelay {

    private final SpringDataOutboxEventRepository repository;
    private final OutboxMessagePublisher publisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(SpringDataOutboxEventRepository repository,
                       OutboxMessagePublisher publisher,
                       PlatformTransactionManager transactionManager,
                       Clock clock,
                       int batchSize,
                       int maxAttempts) {
        if (batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("batchSize e maxAttempts devem ser maiores ou iguais a 1");
        }
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    public int publishPending() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEventJpaEntity> pending = repository.lockPending(batchSize, maxAttempts);
            int count = 0;
            for (OutboxEventJpaEntity event : pending) {
                try {
                    publisher.publish(toMessage(event));
                    event.markPublished(clock.instant());
                    count++;
                } catch (RuntimeException exception) {
                    event.markFailed(describe(exception));
                }
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    private static OutboxMessage toMessage(OutboxEventJpaEntity event) {
        return new OutboxMessage(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                event.getOccurredAt());
    }

    private static String describe(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}