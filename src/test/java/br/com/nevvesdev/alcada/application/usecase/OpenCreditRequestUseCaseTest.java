package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyReuseException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCreditRequestUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final OpenCreditRequestUseCase useCase = new OpenCreditRequestUseCase(repository, CLOCK);

    private static OpenCreditRequestCommand command(String key, String amount) {
        return new OpenCreditRequestCommand(
                IdempotencyKey.of(key),
                Cpf.of("529.982.247-25"),
                Money.of(amount),
                Money.of("4000"));
    }

    @Test
    void shouldCreatePendingRequest() {
        OpenCreditRequestResult result = useCase.execute(command("key-1", "5000"));

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.creditRequest().status()).isEqualTo(CreditRequestStatus.PENDING);
        assertThat(result.creditRequest().createdAt()).isEqualTo(CLOCK.instant());
        assertThat(repository.findById(result.creditRequest().id())).isPresent();
    }

    @Test
    void shouldReturnExistingRequestWhenSameKeyAndSamePayload() {
        OpenCreditRequestResult first = useCase.execute(command("key-1", "5000"));

        OpenCreditRequestResult second = useCase.execute(command("key-1", "5000"));

        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.creditRequest().id()).isEqualTo(first.creditRequest().id());
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void shouldRejectSameKeyWithDifferentPayload() {
        useCase.execute(command("key-1", "5000"));

        assertThatThrownBy(() -> useCase.execute(command("key-1", "6000")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void shouldCreateDistinctRequestsForDistinctKeys() {
        OpenCreditRequestResult first = useCase.execute(command("key-1", "5000"));
        OpenCreditRequestResult second = useCase.execute(command("key-2", "5000"));

        assertThat(second.creditRequest().id()).isNotEqualTo(first.creditRequest().id());
        assertThat(repository.size()).isEqualTo(2);
    }

    @Test
    void shouldReturnWinnerWhenConcurrentRequestTakesTheKeyFirst() {
        CreditRequest winner = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("race"),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                CLOCK.instant());
        OpenCreditRequestUseCase racingUseCase = new OpenCreditRequestUseCase(new RacingRepository(winner), CLOCK);

        OpenCreditRequestResult result = racingUseCase.execute(command("race", "5000"));

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.creditRequest()).isSameAs(winner);
    }

    @Test
    void shouldRejectWhenConcurrentWinnerHasDifferentPayload() {
        CreditRequest winner = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("race"),
                Cpf.of("529.982.247-25"),
                Money.of("9000"),
                Money.of("4000"),
                CLOCK.instant());
        OpenCreditRequestUseCase racingUseCase = new OpenCreditRequestUseCase(new RacingRepository(winner), CLOCK);

        assertThatThrownBy(() -> racingUseCase.execute(command("race", "5000")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    private static final class RacingRepository implements CreditRequestRepository {

        private final CreditRequest winner;
        private boolean winnerVisible;

        private RacingRepository(CreditRequest winner) {
            this.winner = winner;
        }

        @Override
        public void save(CreditRequest request) {
            winnerVisible = true;
            throw new IdempotencyKeyConflictException(request.idempotencyKey());
        }

        @Override
        public Optional<CreditRequest> findById(CreditRequestId id) {
            return Optional.empty();
        }

        @Override
        public Optional<CreditRequest> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return winnerVisible ? Optional.of(winner) : Optional.empty();
        }
    }
}