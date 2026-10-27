package br.com.nevvesdev.alcada.infrastructure.job;

import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "alcada.jobs", name = "enabled", havingValue = "true")
public class OutboxRelayJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxRelay relay;

    public OutboxRelayJob(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(initialDelayString = "${alcada.outbox.initial-delay}", fixedDelayString = "${alcada.outbox.interval}")
    public void publishPending() {
        try {
            int published = relay.publishPending();
            if (published > 0) {
                LOGGER.info("Outbox: {} evento(s) publicado(s)", published);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao processar o outbox", exception);
        }
    }
}