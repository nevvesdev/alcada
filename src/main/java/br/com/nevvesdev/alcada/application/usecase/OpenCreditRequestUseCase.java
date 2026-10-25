package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyReuseException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public final class OpenCreditRequestUseCase {

    private final CreditRequestRepository repository;
    private final Clock clock;

    public OpenCreditRequestUseCase(CreditRequestRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OpenCreditRequestResult execute(OpenCreditRequestCommand command) {
        Optional<CreditRequest> existing = repository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                command.idempotencyKey(),
                command.cpf(),
                command.requestedAmount(),
                command.declaredIncome(),
                clock.instant());
        try {
            repository.save(request);
            return new OpenCreditRequestResult(request, true);
        } catch (IdempotencyKeyConflictException conflict) {
            CreditRequest winner = repository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> conflict);
            return replay(winner, command);
        }
    }

    private OpenCreditRequestResult replay(CreditRequest existing, OpenCreditRequestCommand command) {
        boolean samePayload = existing.cpf().equals(command.cpf())
                && existing.requestedAmount().equals(command.requestedAmount())
                && existing.declaredIncome().equals(command.declaredIncome());
        if (!samePayload) {
            throw new IdempotencyKeyReuseException();
        }
        return new OpenCreditRequestResult(existing, false);
    }
}