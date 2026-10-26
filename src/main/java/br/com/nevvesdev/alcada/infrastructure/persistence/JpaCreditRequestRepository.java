package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaCreditRequestRepository implements CreditRequestRepository {

    private final SpringDataCreditRequestRepository delegate;

    public JpaCreditRequestRepository(SpringDataCreditRequestRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(CreditRequest request) {
        CreditRequestJpaEntity entity = CreditRequestMapper.toEntity(request);
        try {
            delegate.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (isKeyTakenByAnother(entity)) {
                throw new IdempotencyKeyConflictException(request.idempotencyKey());
            }
            throw exception;
        }
    }

    @Override
    public Optional<CreditRequest> findById(CreditRequestId id) {
        return delegate.findById(id.value()).map(CreditRequestMapper::toDomain);
    }

    @Override
    public Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return delegate.findByIdempotencyKey(idempotencyKey.value()).map(CreditRequestMapper::toDomain);
    }

    private boolean isKeyTakenByAnother(CreditRequestJpaEntity entity) {
        return delegate.findByIdempotencyKey(entity.getIdempotencyKey())
                .map(existing -> !existing.getId().equals(entity.getId()))
                .orElse(false);
    }
}

interface SpringDataCreditRequestRepository extends JpaRepository<CreditRequestJpaEntity, UUID> {

    Optional<CreditRequestJpaEntity> findByIdempotencyKey(String idempotencyKey);
}