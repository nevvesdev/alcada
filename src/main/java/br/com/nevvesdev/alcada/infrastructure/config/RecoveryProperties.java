package br.com.nevvesdev.alcada.infrastructure.config;

import java.time.Duration;

public record RecoveryProperties(Duration initialDelay,
                                 Duration interval,
                                 Duration staleAfter,
                                 Duration lease,
                                 int batchSize) {

    public RecoveryProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("Propriedade recovery.initial-delay deve ser uma duração não negativa");
        }
        requirePositive(interval, "recovery.interval");
        requirePositive(staleAfter, "recovery.stale-after");
        requirePositive(lease, "recovery.lease");
        if (batchSize < 1) {
            throw new IllegalArgumentException("Propriedade recovery.batch-size deve ser maior ou igual a 1");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Propriedade " + property + " deve ser uma duração positiva");
        }
    }
}