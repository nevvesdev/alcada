package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.usecase.GetCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ProcessCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ResolveManualReviewUseCase;
import br.com.nevvesdev.alcada.infrastructure.bureau.HttpCreditBureauAdapter;
import br.com.nevvesdev.alcada.infrastructure.limit.HttpLimitReservationAdapter;
import br.com.nevvesdev.alcada.infrastructure.persistence.JpaCreditRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationWiringTest {

    @Autowired
    private CreditRequestRepository repository;

    @Autowired
    private CreditBureauPort bureau;

    @Autowired
    private LimitReservationPort limits;

    @Autowired
    private CreditSagaOrchestrator orchestrator;

    @Autowired
    private OpenCreditRequestUseCase openUseCase;

    @Autowired
    private ProcessCreditRequestUseCase processUseCase;

    @Autowired
    private GetCreditRequestUseCase getUseCase;

    @Autowired
    private ResolveManualReviewUseCase reviewUseCase;

    @Autowired
    private AlcadaProperties properties;

    @Test
    void shouldWirePortsWithTheirAdapters() {
        assertThat(repository).isInstanceOf(JpaCreditRequestRepository.class);
        assertThat(bureau).isInstanceOf(HttpCreditBureauAdapter.class);
        assertThat(limits).isInstanceOf(HttpLimitReservationAdapter.class);
    }

    @Test
    void shouldWireOrchestratorAndUseCases() {
        assertThat(orchestrator).isNotNull();
        assertThat(openUseCase).isNotNull();
        assertThat(processUseCase).isNotNull();
        assertThat(getUseCase).isNotNull();
        assertThat(reviewUseCase).isNotNull();
    }

    @Test
    void shouldBindRemoteServiceProperties() {
        assertThat(properties.bureau().readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.bureau().retry().maxAttempts()).isEqualTo(3);
        assertThat(properties.bureau().circuitBreaker().failureRateThreshold()).isEqualTo(50f);
        assertThat(properties.limits().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.limits().circuitBreaker().halfOpenPermittedCalls()).isEqualTo(3);
    }
}