package br.com.nevvesdev.alcada.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.Supplier;

public final class ResilientCaller {

    private static final double JITTER_FACTOR = 0.2;

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientCaller(String name, RetryProperties retryProperties, CircuitBreakerProperties breakerProperties) {
        this(name, retryProperties, breakerProperties, null);
    }

    public ResilientCaller(String name,
                           RetryProperties retryProperties,
                           CircuitBreakerProperties breakerProperties,
                           MeterRegistry meterRegistry) {
        CircuitBreakerRegistry breakerRegistry = CircuitBreakerRegistry.ofDefaults();
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        this.circuitBreaker = breakerRegistry.circuitBreaker(name, circuitBreakerConfig(breakerProperties));
        this.retry = retryRegistry.retry(name, retryConfig(retryProperties));
        if (meterRegistry != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakerRegistry).bindTo(meterRegistry);
            TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
        }
    }

    public <T> T execute(Supplier<T> call) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        return Retry.decorateSupplier(retry, guarded).get();
    }

    public void run(Runnable call) {
        execute(() -> {
            call.run();
            return null;
        });
    }

    public boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    private static CircuitBreakerConfig circuitBreakerConfig(CircuitBreakerProperties properties) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .waitDurationInOpenState(properties.openStateDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenPermittedCalls())
                .recordException(TransientRemoteException.class::isInstance)
                .build();
    }

    private static RetryConfig retryConfig(RetryProperties properties) {
        return RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        properties.initialBackoff().toMillis(),
                        properties.multiplier(),
                        JITTER_FACTOR))
                .retryOnException(TransientRemoteException.class::isInstance)
                .build();
    }
}