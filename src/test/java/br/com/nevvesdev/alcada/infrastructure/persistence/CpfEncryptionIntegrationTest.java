package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CpfEncryptionIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired
    private JpaCreditRequestRepository repository;

    @Autowired
    private SpringDataCreditRequestRepository creditRequests;

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        outbox.deleteAll();
        creditRequests.deleteAll();
    }

    private static CreditRequest newRequest(String key) {
        return CreditRequest.open(
                CreditRequestId.generate(),
                IdempotencyKey.of(key),
                Cpf.of("529.982.247-25"),
                Money.of("5000"),
                Money.of("4000"),
                T0);
    }

    private String storedCpf(CreditRequest request) {
        return jdbc.queryForObject("SELECT cpf FROM credit_request WHERE id = ?", String.class, request.id().value());
    }

    @Test
    void shouldStoreCpfEncryptedAndReadItBack() {
        CreditRequest request = newRequest("key-1");

        repository.save(request);

        assertThat(storedCpf(request)).startsWith("enc:v1:").doesNotContain("52998224725");
        assertThat(repository.findById(request.id()).orElseThrow().cpf()).isEqualTo(Cpf.of("529.982.247-25"));
    }

    @Test
    void shouldStoreDifferentCiphertextForTheSameCpf() {
        CreditRequest first = newRequest("key-1");
        CreditRequest second = newRequest("key-2");

        repository.save(first);
        repository.save(second);

        assertThat(storedCpf(first)).isNotEqualTo(storedCpf(second));
    }

    @Test
    void shouldReadLegacyClearTextRows() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_request "
                        + "(id, idempotency_key, cpf, requested_amount, declared_income, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                id, "legacy-1", "52998224725", new BigDecimal("5000.00"), new BigDecimal("4000.00"),
                Timestamp.from(T0), Timestamp.from(T0));

        CreditRequest loaded = repository.findById(new CreditRequestId(id)).orElseThrow();

        assertThat(loaded.cpf()).isEqualTo(Cpf.of("52998224725"));
    }

    @Test
    void shouldKeepTheCpfEncryptedWhenTheRequestIsUpdated() {
        CreditRequest request = newRequest("key-1");
        repository.save(request);
        request.registerBureauScore(br.com.nevvesdev.alcada.domain.model.CreditScore.of(700), T0.plusSeconds(1));

        repository.save(request);

        assertThat(storedCpf(request)).startsWith("enc:v1:");
        assertThat(repository.findById(request.id()).orElseThrow().cpf()).isEqualTo(Cpf.of("529.982.247-25"));
    }
}