package br.com.nevvesdev.alcada.infrastructure.http;

import java.time.Duration;

public record RetryProperties(int maxAttempts, Duration initialBackoff, double multiplier) {

    public RetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Propriedade retry.max-attempts deve ser maior ou igual a 1");
        }
        PropertyValidation.requirePositive(initialBackoff, "retry.initial-backoff");
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("Propriedade retry.multiplier deve ser maior ou igual a 1");
        }
    }
}