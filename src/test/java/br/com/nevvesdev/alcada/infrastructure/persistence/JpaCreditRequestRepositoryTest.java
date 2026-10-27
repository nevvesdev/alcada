package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.application.exception.IdempotencyKeyConflictException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaCreditRequestRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-26T12:00:00Z");

    @Autowired
    private JpaCreditRequestRepository repository;

    @Autowired
    private SpringDataCreditRequestRepository springData;

    @BeforeEach
    void cleanDatabase() {
        springData.deleteAll();
    }

    private static CreditRequest newRequest(String key) {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of(key),
                Cpf.of("529.982.247-25"),
                Money.of("5000.50"),
                Money.of("4000"),
                T0);
    }

    @Test
    void shouldPersistAndLoadPendingRequest() {
        CreditRequest request = newRequest("key-1");

        repository.save(request);

        CreditRequest loaded = repository.findById(request.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().ignoringFields("domainEvents").isEqualTo(request);
    }

    @Test
    void shouldPersistEachStepOfTheSaga() {
        CreditRequest request = newRequest("key-1");
        repository.save(request);

        request.registerBureauScore(CreditScore.of(700), T0.plusSeconds(1));
        repository.save(request);
        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(CreditRequestStatus.BUREAU_CHECKED);

        request.registerRiskDecision(RiskDecision.approve(), T0.plusSeconds(2));
        repository.save(request);
        request.reserveLimit(T0.plusSeconds(3));
        repository.save(request);
        request.approve(T0.plusSeconds(4));
        repository.save(request);

        CreditRequest loaded = repository.findById(request.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(CreditRequestStatus.APPROVED);
        assertThat(loaded).usingRecursiveComparison().ignoringFields("domainEvents").isEqualTo(request);
        assertThat(springData.count()).isEqualTo(1);
    }

    @Test
    void shouldPersistManualReviewDecision() {
        CreditRequest request = newRequest("key-1");
        request.registerBureauScore(CreditScore.of(500), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.manualReview(), T0.plusSeconds(2));
        request.sendToReview(T0.plusSeconds(3));

        repository.save(request);

        CreditRequest loaded = repository.findById(request.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(CreditRequestStatus.UNDER_REVIEW);
        assertThat(loaded.riskDecision().requiresManualReview()).isTrue();
    }

    @Test
    void shouldPersistRejectionWithDeniedDecision() {
        CreditRequest request = newRequest("key-1");
        request.registerBureauScore(CreditScore.of(300), T0.plusSeconds(1));
        request.registerRiskDecision(RiskDecision.deny(RejectionReason.LOW_SCORE), T0.plusSeconds(2));
        request.reject(RejectionReason.LOW_SCORE, T0.plusSeconds(3));

        repository.save(request);

        CreditRequest loaded = repository.findById(request.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().ignoringFields("domainEvents").isEqualTo(request);
    }

    @Test
    void shouldPersistRejectionWithoutScore() {
        CreditRequest request = newRequest("key-1");
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));

        repository.save(request);

        CreditRequest loaded = repository.findById(request.id()).orElseThrow();
        assertThat(loaded.creditScore()).isNull();
        assertThat(loaded.rejectionReason()).isEqualTo(RejectionReason.BUREAU_UNAVAILABLE);
    }

    @Test
    void shouldFindRequestByIdempotencyKey() {
        CreditRequest request = newRequest("key-1");
        repository.save(request);

        CreditRequest loaded = repository.findByIdempotencyKey(IdempotencyKey.of("key-1")).orElseThrow();

        assertThat(loaded.id()).isEqualTo(request.id());
    }

    @Test
    void shouldReturnEmptyWhenRequestDoesNotExist() {
        assertThat(repository.findById(CreditRequestId.generate())).isEmpty();
        assertThat(repository.findByIdempotencyKey(IdempotencyKey.of("missing"))).isEmpty();
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyFromAnotherRequest() {
        repository.save(newRequest("key-1"));
        CreditRequest duplicate = newRequest("key-1");

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(springData.count()).isEqualTo(1);
        assertThat(repository.findById(duplicate.id())).isEmpty();
    }

    @Test
    void shouldAllowUpdatingRequestThatOwnsTheKey() {
        CreditRequest request = newRequest("key-1");
        repository.save(request);
        request.reject(RejectionReason.BUREAU_UNAVAILABLE, T0.plusSeconds(1));

        repository.save(request);

        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(CreditRequestStatus.REJECTED);
        assertThat(springData.count()).isEqualTo(1);
    }
}