package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaStalledCreditRequestAdapterTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant NOW = T0.plus(Duration.ofMinutes(10));
    private static final Instant STALE_BEFORE = NOW.minus(Duration.ofMinutes(1));
    private static final Duration LEASE = Duration.ofMinutes(2);

    @Autowired
    private JpaCreditRequestRepository repository;

    @Autowired
    private JpaStalledCreditRequestAdapter adapter;

    @Autowired
    private SpringDataCreditRequestRepository creditRequests;

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        creditRequests.deleteAll();
    }

    private CreditRequest saved(String key, Instant createdAt) {
        CreditRequest request = CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of(key),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                createdAt);
        repository.save(request);
        return request;
    }

    private CreditRequest savedLimitReserved(String key, Instant createdAt) {
        CreditRequest request = saved(key, createdAt);
        request.registerBureauScore(CreditScore.of(700), createdAt.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.approve(), createdAt.plusSeconds(2));
        request.reserveLimit(createdAt.plusSeconds(3));
        repository.save(request);
        return request;
    }

    private CreditRequest savedUnderReview(String key, Instant createdAt) {
        CreditRequest request = saved(key, createdAt);
        request.registerBureauScore(CreditScore.of(500), createdAt.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.manualReview(), createdAt.plusSeconds(2));
        request.sendToReview(createdAt.plusSeconds(3));
        repository.save(request);
        return request;
    }

    @Test
    void shouldClaimOnlyStaleNonTerminalRequestsThatAreNotWaitingForReview() {
        CreditRequest stalePending = saved("stale-pending", T0);
        CreditRequest staleReserved = savedLimitReserved("stale-reserved", T0.plusSeconds(10));
        saved("recent", NOW.minusSeconds(30));
        savedUnderReview("under-review", T0);
        CreditRequest rejected = saved("rejected", T0);
        rejected.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));
        repository.save(rejected);

        List<CreditRequestId> claimed = adapter.claim(STALE_BEFORE, NOW, LEASE, 10);

        assertThat(claimed).containsExactlyInAnyOrder(stalePending.id(), staleReserved.id());
    }

    @Test
    void shouldNotClaimRequestsWhileLeaseIsActive() {
        saved("stale-1", T0);
        saved("stale-2", T0.plusSeconds(1));

        assertThat(adapter.claim(STALE_BEFORE, NOW, LEASE, 10)).hasSize(2);
        assertThat(adapter.claim(STALE_BEFORE, NOW, LEASE, 10)).isEmpty();
    }

    @Test
    void shouldClaimAgainAfterLeaseExpires() {
        saved("stale-1", T0);
        adapter.claim(STALE_BEFORE, NOW, LEASE, 10);

        List<CreditRequestId> claimed = adapter.claim(STALE_BEFORE, NOW.plus(LEASE).plusSeconds(1), LEASE, 10);

        assertThat(claimed).hasSize(1);
    }

    @Test
    void shouldRespectBatchSizeAndClaimOldestFirst() {
        CreditRequest oldest = saved("oldest", T0);
        CreditRequest middle = saved("middle", T0.plusSeconds(1));
        saved("newest", T0.plusSeconds(2));

        List<CreditRequestId> claimed = adapter.claim(STALE_BEFORE, NOW, LEASE, 2);

        assertThat(claimed).containsExactly(oldest.id(), middle.id());
    }

    @Test
    void shouldKeepLeaseWhenRequestIsSavedAgain() {
        CreditRequest request = saved("stale-1", T0);
        adapter.claim(STALE_BEFORE, NOW, LEASE, 10);

        request.registerBureauScore(CreditScore.of(700), T0.plusSeconds(1));
        repository.save(request);

        assertThat(adapter.claim(STALE_BEFORE, NOW, LEASE, 10)).isEmpty();
    }

    @Test
    void shouldSkipRequestsLockedByAnotherTransaction() {
        saved("stale-1", T0);
        saved("stale-2", T0.plusSeconds(1));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            creditRequests.lockStalled(STALE_BEFORE, NOW, 10);
            List<CreditRequestId> claimedByOther = CompletableFuture
                    .supplyAsync(() -> adapter.claim(STALE_BEFORE, NOW, LEASE, 10))
                    .join();
            assertThat(claimedByOther).isEmpty();
        });

        assertThat(adapter.claim(STALE_BEFORE, NOW, LEASE, 10)).hasSize(2);
    }
}