package br.com.nevvesdev.alcada.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Transactional
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL AND attempts < :maxAttempts
            ORDER BY occurred_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockPending(@Param("batchSize") int batchSize,
                                           @Param("maxAttempts") int maxAttempts);

    long countByPublishedAtIsNullAndAttemptsLessThan(int attempts);

    long countByPublishedAtIsNullAndAttemptsGreaterThanEqual(int attempts);
}