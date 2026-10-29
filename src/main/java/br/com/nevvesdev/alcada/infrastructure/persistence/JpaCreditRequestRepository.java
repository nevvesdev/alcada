package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.infrastructure.metrics.CreditRequestMetrics;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaCreditRequestRepository implements CreditRequestRepository {

    private final SpringDataCreditRequestRepository creditRequests;
    private final SpringDataOutboxEventRepository outboxEvents;
    private final CreditRequestMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public JpaCreditRequestRepository(SpringDataCreditRequestRepository creditRequests,
                                      SpringDataOutboxEventRepository outboxEvents,
                                      CreditRequestMetrics metrics,
                                      PlatformTransactionManager transactionManager) {
        this.creditRequests = creditRequests;
        this.outboxEvents = outboxEvents;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(CreditRequest request) {
        CreditRequestJpaEntity entity = CreditRequestMapper.toEntity(request);
        List<OutboxEventJpaEntity> events = request.domainEvents().stream()
                .map(OutboxEventMapper::toEntity)
                .toList();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                creditRequests.saveAndFlush(entity);
                outboxEvents.saveAll(events);
            });
        } catch (DataIntegrityViolationException exception) {
            if (isKeyTakenByAnother(entity)) {
                throw new IdempotencyKeyConflictException(request.idempotencyKey());
            }
            throw exception;
        }
        request.domainEvents().forEach(metrics::recordOutcome);
        request.clearDomainEvents();
    }

    @Override
    public Optional<CreditRequest> findById(CreditRequestId id) {
        return creditRequests.findById(id.value()).map(CreditRequestMapper::toDomain);
    }

    @Override
    public Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return creditRequests.findByIdempotencyKey(idempotencyKey.value()).map(CreditRequestMapper::toDomain);
    }

    private boolean isKeyTakenByAnother(CreditRequestJpaEntity entity) {
        return creditRequests.findByIdempotencyKey(entity.getIdempotencyKey())
                .map(existing -> !existing.getId().equals(entity.getId()))
                .orElse(false);
    }
}