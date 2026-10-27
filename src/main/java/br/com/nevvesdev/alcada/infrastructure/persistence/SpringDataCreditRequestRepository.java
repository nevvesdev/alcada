package br.com.nevvesdev.alcada.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCreditRequestRepository extends JpaRepository<CreditRequestJpaEntity, UUID> {

    Optional<CreditRequestJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Transactional
    @Query(value = """
            SELECT id FROM credit_request
            WHERE status IN ('PENDING', 'BUREAU_CHECKED', 'RISK_ANALYZED', 'LIMIT_RESERVED')
              AND updated_at < :staleBefore
              AND (claimed_until IS NULL OR claimed_until < :now)
            ORDER BY updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockStalled(@Param("staleBefore") Instant staleBefore,
                           @Param("now") Instant now,
                           @Param("batchSize") int batchSize);

    @Transactional
    @Modifying
    @Query(value = "UPDATE credit_request SET claimed_until = :claimedUntil WHERE id IN (:ids)",
            nativeQuery = true)
    int claim(@Param("ids") Collection<UUID> ids, @Param("claimedUntil") Instant claimedUntil);
}