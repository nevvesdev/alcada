package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetCreditRequestUseCaseTest {

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final GetCreditRequestUseCase useCase = new GetCreditRequestUseCase(repository);

    @Test
    void shouldReturnExistingRequest() {
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-1"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                Instant.parse("2026-10-25T12:00:00Z"));
        repository.save(request);

        assertThat(useCase.execute(request.id())).isSameAs(request);
    }

    @Test
    void shouldFailWhenRequestDoesNotExist() {
        assertThatThrownBy(() -> useCase.execute(CreditRequestId.generate()))
                .isInstanceOf(CreditRequestNotFoundException.class);
    }
}