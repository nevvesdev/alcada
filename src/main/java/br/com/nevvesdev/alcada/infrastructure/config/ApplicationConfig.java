package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.application.port.out.StalledCreditRequestPort;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.usecase.GetCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ProcessCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.RecoverStalledRequestsUseCase;
import br.com.nevvesdev.alcada.application.usecase.ResolveManualReviewUseCase;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import br.com.nevvesdev.alcada.infrastructure.bureau.HttpCreditBureauAdapter;
import br.com.nevvesdev.alcada.infrastructure.limit.HttpLimitReservationAdapter;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessagePublisher;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxRelay;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlcadaProperties.class)
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RiskPolicy riskPolicy() {
        return RiskPolicy.standard();
    }

    @Bean
    CreditBureauPort creditBureauPort(AlcadaProperties properties, MeterRegistry meterRegistry) {
        return new HttpCreditBureauAdapter(properties.bureau(), meterRegistry);
    }

    @Bean
    LimitReservationPort limitReservationPort(AlcadaProperties properties, MeterRegistry meterRegistry) {
        return new HttpLimitReservationAdapter(properties.limits(), meterRegistry);
    }

    @Bean
    CreditSagaOrchestrator creditSagaOrchestrator(CreditRequestRepository repository,
                                                  CreditBureauPort bureau,
                                                  LimitReservationPort limits,
                                                  RiskPolicy riskPolicy,
                                                  Clock clock) {
        return new CreditSagaOrchestrator(repository, bureau, limits, riskPolicy, clock);
    }

    @Bean
    OpenCreditRequestUseCase openCreditRequestUseCase(CreditRequestRepository repository, Clock clock) {
        return new OpenCreditRequestUseCase(repository, clock);
    }

    @Bean
    ProcessCreditRequestUseCase processCreditRequestUseCase(CreditRequestRepository repository,
                                                            CreditSagaOrchestrator orchestrator) {
        return new ProcessCreditRequestUseCase(repository, orchestrator);
    }

    @Bean
    GetCreditRequestUseCase getCreditRequestUseCase(CreditRequestRepository repository) {
        return new GetCreditRequestUseCase(repository);
    }

    @Bean
    ResolveManualReviewUseCase resolveManualReviewUseCase(CreditRequestRepository repository,
                                                          CreditSagaOrchestrator orchestrator) {
        return new ResolveManualReviewUseCase(repository, orchestrator);
    }

    @Bean
    RecoverStalledRequestsUseCase recoverStalledRequestsUseCase(StalledCreditRequestPort stalledRequests,
                                                                CreditRequestRepository repository,
                                                                CreditSagaOrchestrator orchestrator,
                                                                Clock clock,
                                                                AlcadaProperties properties) {
        RecoveryProperties recovery = properties.recovery();
        return new RecoverStalledRequestsUseCase(
                stalledRequests,
                repository,
                orchestrator,
                clock,
                recovery.staleAfter(),
                recovery.lease(),
                recovery.batchSize());
    }

    @Bean
    OutboxRelay outboxRelay(SpringDataOutboxEventRepository repository,
                            OutboxMessagePublisher publisher,
                            PlatformTransactionManager transactionManager,
                            Clock clock,
                            AlcadaProperties properties) {
        OutboxProperties outbox = properties.outbox();
        return new OutboxRelay(
                repository, publisher, transactionManager, clock, outbox.batchSize(), outbox.maxAttempts());
    }
}