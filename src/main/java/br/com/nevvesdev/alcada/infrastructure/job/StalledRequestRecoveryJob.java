package br.com.nevvesdev.alcada.infrastructure.job;

import br.com.nevvesdev.alcada.application.usecase.RecoverStalledRequestsUseCase;
import br.com.nevvesdev.alcada.application.usecase.RecoverySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "alcada.jobs", name = "enabled", havingValue = "true")
public class StalledRequestRecoveryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StalledRequestRecoveryJob.class);

    private final RecoverStalledRequestsUseCase useCase;

    public StalledRequestRecoveryJob(RecoverStalledRequestsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(initialDelayString = "${alcada.recovery.initial-delay}", fixedDelayString = "${alcada.recovery.interval}")
    public void recover() {
        try {
            RecoverySummary summary = useCase.execute();
            if (summary.claimed() > 0) {
                LOGGER.info("Recuperação: {} reivindicada(s), {} retomada(s), {} com falha {}",
                        summary.claimed(), summary.recovered(), summary.failed().size(), summary.failed());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao recuperar solicitações paradas", exception);
        }
    }
}