package br.com.nevvesdev.alcada.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxMessagePublisher implements OutboxMessagePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxMessagePublisher.class);

    @Override
    public void publish(OutboxMessage message) {
        LOGGER.info("Evento publicado: tipo={} agregado={} id={}",
                message.eventType(), message.aggregateId(), message.id());
        LOGGER.debug("Payload do evento {}: {}", message.id(), message.payload());
    }
}