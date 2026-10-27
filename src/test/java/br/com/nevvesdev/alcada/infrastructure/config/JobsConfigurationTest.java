package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.application.usecase.RecoverStalledRequestsUseCase;
import br.com.nevvesdev.alcada.infrastructure.job.OutboxRelayJob;
import br.com.nevvesdev.alcada.infrastructure.job.StalledRequestRecoveryJob;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobsConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxRelayJob.class, StalledRequestRecoveryJob.class)
            .withBean(OutboxRelay.class, () -> mock(OutboxRelay.class))
            .withBean(RecoverStalledRequestsUseCase.class, () -> mock(RecoverStalledRequestsUseCase.class));

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AlcadaProperties properties;

    @Test
    void shouldBindRecoveryProperties() {
        RecoveryProperties recovery = properties.recovery();

        assertThat(recovery.interval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(recovery.staleAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(recovery.lease()).isEqualTo(Duration.ofMinutes(2));
        assertThat(recovery.batchSize()).isEqualTo(20);
    }

    @Test
    void shouldBindOutboxProperties() {
        OutboxProperties outbox = properties.outbox();

        assertThat(outbox.interval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(outbox.batchSize()).isEqualTo(50);
        assertThat(outbox.maxAttempts()).isEqualTo(10);
    }

    @Test
    void shouldKeepJobsDisabledDuringTests() {
        assertThat(context.getBeansOfType(OutboxRelayJob.class)).isEmpty();
        assertThat(context.getBeansOfType(StalledRequestRecoveryJob.class)).isEmpty();
    }

    @Test
    void shouldCreateJobsWhenEnabled() {
        runner.withPropertyValues("alcada.jobs.enabled=true").run(runnerContext -> {
            assertThat(runnerContext).hasSingleBean(OutboxRelayJob.class);
            assertThat(runnerContext).hasSingleBean(StalledRequestRecoveryJob.class);
        });
    }

    @Test
    void shouldNotCreateJobsWhenDisabledOrMissing() {
        runner.withPropertyValues("alcada.jobs.enabled=false").run(runnerContext -> {
            assertThat(runnerContext).doesNotHaveBean(OutboxRelayJob.class);
            assertThat(runnerContext).doesNotHaveBean(StalledRequestRecoveryJob.class);
        });
        runner.run(runnerContext -> {
            assertThat(runnerContext).doesNotHaveBean(OutboxRelayJob.class);
            assertThat(runnerContext).doesNotHaveBean(StalledRequestRecoveryJob.class);
        });
    }
}