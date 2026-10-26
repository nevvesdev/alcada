package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.application.usecase.GetCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.OpenCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ProcessCreditRequestUseCase;
import br.com.nevvesdev.alcada.application.usecase.ResolveManualReviewUseCase;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;
import br.com.nevvesdev.alcada.infrastructure.bureau.HttpCreditBureauAdapter;
import br.com.nevvesdev.alcada.infrastructure.limit.HttpLimitReservationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    CreditBureauPort creditBureauPort(AlcadaProperties properties) {
        return new HttpCreditBureauAdapter(properties.bureau());
    }

    @Bean
    LimitReservationPort limitReservationPort(AlcadaProperties properties) {
        return new HttpLimitReservationAdapter(properties.limits());
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
}