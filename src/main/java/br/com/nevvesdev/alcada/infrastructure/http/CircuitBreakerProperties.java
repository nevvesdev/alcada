package br.com.nevvesdev.alcada.infrastructure.http;

import java.time.Duration;

public record CircuitBreakerProperties(float failureRateThreshold,
                                       int slidingWindowSize,
                                       int minimumNumberOfCalls,
                                       Duration openStateDuration,
                                       int halfOpenPermittedCalls) {

    public CircuitBreakerProperties {
        if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
            throw new IllegalArgumentException(
                    "Propriedade circuit-breaker.failure-rate-threshold deve estar entre 0 (exclusivo) e 100");
        }
        if (slidingWindowSize < 1) {
            throw new IllegalArgumentException("Propriedade circuit-breaker.sliding-window-size deve ser maior ou igual a 1");
        }
        if (minimumNumberOfCalls < 1) {
            throw new IllegalArgumentException(
                    "Propriedade circuit-breaker.minimum-number-of-calls deve ser maior ou igual a 1");
        }
        PropertyValidation.requirePositive(openStateDuration, "circuit-breaker.open-state-duration");
        if (halfOpenPermittedCalls < 1) {
            throw new IllegalArgumentException(
                    "Propriedade circuit-breaker.half-open-permitted-calls deve ser maior ou igual a 1");
        }
    }
}