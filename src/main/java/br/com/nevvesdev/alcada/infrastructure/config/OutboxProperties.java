package br.com.nevvesdev.alcada.infrastructure.config;

import java.time.Duration;

public record OutboxProperties(Duration initialDelay, Duration interval, int batchSize, int maxAttempts) {

    public OutboxProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("Propriedade outbox.initial-delay deve ser uma duração não negativa");
        }
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Propriedade outbox.interval deve ser uma duração positiva");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Propriedade outbox.batch-size deve ser maior ou igual a 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Propriedade outbox.max-attempts deve ser maior ou igual a 1");
        }
    }
}