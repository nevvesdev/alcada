package br.com.nevvesdev.alcada.application.port.out;

import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;

import java.util.Optional;

public interface CreditRequestRepository {

    void save(CreditRequest request);

    Optional<CreditRequest> findById(CreditRequestId id);

    Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}