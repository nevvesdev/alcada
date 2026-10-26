package br.com.nevvesdev.alcada.infrastructure.http;

import java.time.Duration;
import java.util.Objects;

public record RemoteServiceProperties(String baseUrl,
                                      Duration connectTimeout,
                                      Duration readTimeout,
                                      RetryProperties retry,
                                      CircuitBreakerProperties circuitBreaker) {

    public RemoteServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Propriedade base-url é obrigatória");
        }
        PropertyValidation.requirePositive(connectTimeout, "connect-timeout");
        PropertyValidation.requirePositive(readTimeout, "read-timeout");
        Objects.requireNonNull(retry, "Propriedades de retry são obrigatórias");
        Objects.requireNonNull(circuitBreaker, "Propriedades de circuit-breaker são obrigatórias");
    }
}