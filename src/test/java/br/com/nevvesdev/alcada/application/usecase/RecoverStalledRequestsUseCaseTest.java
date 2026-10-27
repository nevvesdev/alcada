package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.support.FakeCreditBureau;
import br.com.nevvesdev.alcada.application.support.FakeLimitReservation;
import br.com.nevvesdev.alcada.application.support.FakeStalledCreditRequestPort;
import br.com.nevvesdev.alcada.application.support.InMemoryCreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecoverStalledRequestsUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryCreditRequestRepository repository = new InMemoryCreditRequestRepository();
    private final FakeCreditBureau bureau = new FakeCreditBureau();
    private final FakeLimitReservation limits = new FakeLimitReservation();
    private final FakeStalledCreditRequestPort stalledRequests = new FakeStalledCreditRequestPort();
    private final CreditSagaOrchestrator orchestrator =
            new CreditSagaOrchestrator(repository, bureau, limits, RiskPolicy.standard(), CLOCK);
    private final RecoverStalledRequestsUseCase useCase = new RecoverStalledRequestsUseCase(
            stalledRequests, repository, orchestrator, CLOCK, Duration.ofMinutes(1), Duration.ofMinutes(2), 20);

    private CreditRequest stalledRequest() {
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of("key-" + UUID.randomUUID()),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                CLOCK.instant().minusSeconds(600));
        repository.save(request);
        return request;
    }

    @Test
    void shouldAskForStalledRequestsUsingConfiguredWindow() {
        useCase.execute();

        assertThat(stalledRequests.lastNow()).isEqualTo(CLOCK.instant());
        assertThat(stalledRequests.lastStaleBefore()).isEqualTo(CLOCK.instant().minus(Duration.ofMinutes(1)));
        assertThat(stalledRequests.lastLease()).isEqualTo(Duration.ofMinutes(2));
        assertThat(stalledRequests.lastBatchSize()).isEqualTo(20);
    }

    @Test
    void shouldReturnEmptySummaryWhenNothingIsClaimed() {
        RecoverySummary summary = useCase.execute();

        assertThat(summary.claimed()).isZero();
        assertThat(summary.recovered()).isZero();
        assertThat(summary.failed()).isEmpty();
    }

    @Test
    void shouldResumeClaimedRequests() {
        bureau.returning(700);
        CreditRequest request = stalledRequest();
        stalledRequests.willReturn(request.id());

        RecoverySummary summary = useCase.execute();

        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.recovered()).isEqualTo(1);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
    }

    @Test
    void shouldContinueWhenOneClaimedRequestDoesNotExist() {
        bureau.returning(700);
        CreditRequestId missing = CreditRequestId.generate();
        CreditRequest request = stalledRequest();
        stalledRequests.willReturn(missing, request.id());

        RecoverySummary summary = useCase.execute();

        assertThat(summary.claimed()).isEqualTo(2);
        assertThat(summary.failed()).containsExactly(missing);
        assertThat(summary.recovered()).isEqualTo(1);
        assertThat(request.status()).isEqualTo(CreditRequestStatus.APPROVED);
    }

    @Test
    void shouldReportRequestWhoseRecoveryFailsAndKeepItResumable() {
        bureau.returning(700);
        limits.failConfirm(true);
        limits.failRelease(true);
        CreditRequest request = stalledRequest();
        stalledRequests.willReturn(request.id());

        RecoverySummary summary = useCase.execute();

        assertThat(summary.failed()).containsExactly(request.id());
        assertThat(request.status()).isEqualTo(CreditRequestStatus.LIMIT_RESERVED);
    }
}