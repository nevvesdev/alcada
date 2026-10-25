package br.com.nevvesdev.alcada.application.support;

import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryCreditRequestRepository implements CreditRequestRepository {

    private final Map<CreditRequestId, CreditRequest> storage = new LinkedHashMap<>();

    @Override
    public void save(CreditRequest request) {
        boolean keyTakenByAnother = storage.values().stream()
                .anyMatch(existing -> existing.idempotencyKey().equals(request.idempotencyKey())
                        && !existing.id().equals(request.id()));
        if (keyTakenByAnother) {
            throw new IdempotencyKeyConflictException(request.idempotencyKey());
        }
        storage.put(request.id(), request);
    }

    @Override
    public Optional<CreditRequest> findById(CreditRequestId id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return storage.values().stream()
                .filter(request -> request.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    public int size() {
        return storage.size();
    }
}