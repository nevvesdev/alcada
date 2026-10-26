package br.com.nevvesdev.alcada.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.function.Supplier;

public final class ResilientCaller {

    private static final double JITTER_FACTOR = 0.2;

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientCaller(String name, RetryProperties retryProperties, CircuitBreakerProperties breakerProperties) {
        this.circuitBreaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .failureRateThreshold(breakerProperties.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(breakerProperties.slidingWindowSize())
                .minimumNumberOfCalls(breakerProperties.minimumNumberOfCalls())
                .waitDurationInOpenState(breakerProperties.openStateDuration())
                .permittedNumberOfCallsInHalfOpenState(breakerProperties.halfOpenPermittedCalls())
                .recordException(TransientRemoteException.class::isInstance)
                .build());
        this.retry = Retry.of(name, RetryConfig.custom()
                .maxAttempts(retryProperties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        retryProperties.initialBackoff().toMillis(),
                        retryProperties.multiplier(),
                        JITTER_FACTOR))
                .retryOnException(TransientRemoteException.class::isInstance)
                .build());
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
}