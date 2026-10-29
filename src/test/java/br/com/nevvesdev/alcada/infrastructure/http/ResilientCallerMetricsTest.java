package br.com.nevvesdev.alcada.infrastructure.http;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientCallerMetricsTest {

    private static boolean hasMeterNamed(SimpleMeterRegistry registry, String prefix, String callerName) {
        return registry.getMeters().stream()
                .anyMatch(meter -> meter.getId().getName().startsWith(prefix)
                        && callerName.equals(meter.getId().getTag("name")));
    }

    @Test
    void shouldPublishCircuitBreakerAndRetryMetricsWhenRegistryIsProvided() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ResilientCaller caller = new ResilientCaller(
                "metered",
                new RetryProperties(2, Duration.ofMillis(1), 2.0),
                new CircuitBreakerProperties(50f, 10, 5, Duration.ofSeconds(30), 1),
                registry);

        String result = caller.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(hasMeterNamed(registry, "resilience4j.circuitbreaker", "metered")).isTrue();
        assertThat(hasMeterNamed(registry, "resilience4j.retry", "metered")).isTrue();
    }

    @Test
    void shouldKeepWorkingWithoutRegistry() {
        ResilientCaller caller = new ResilientCaller(
                "plain",
                new RetryProperties(2, Duration.ofMillis(1), 2.0),
                new CircuitBreakerProperties(50f, 10, 5, Duration.ofSeconds(30), 1));

        assertThat(caller.<String>execute(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void shouldKeepMetricsOfDifferentCallersApart() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RetryProperties retry = new RetryProperties(1, Duration.ofMillis(1), 1.0);
        CircuitBreakerProperties breaker = new CircuitBreakerProperties(50f, 10, 5, Duration.ofSeconds(30), 1);

        new ResilientCaller("bureau-x", retry, breaker, registry);
        new ResilientCaller("limits-x", retry, breaker, registry);

        assertThat(hasMeterNamed(registry, "resilience4j.circuitbreaker", "bureau-x")).isTrue();
        assertThat(hasMeterNamed(registry, "resilience4j.circuitbreaker", "limits-x")).isTrue();
    }
}