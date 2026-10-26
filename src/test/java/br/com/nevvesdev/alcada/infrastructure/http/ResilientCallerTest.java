package br.com.nevvesdev.alcada.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientCallerTest {

    private static RetryProperties retry(int attempts) {
        return new RetryProperties(attempts, Duration.ofMillis(1), 2.0);
    }

    private static CircuitBreakerProperties breaker(int window) {
        return new CircuitBreakerProperties(50f, window, window, Duration.ofSeconds(30), 1);
    }

    @Test
    void shouldRetryTransientFailuresUntilMaxAttempts() {
        ResilientCaller caller = new ResilientCaller("retry-exhausted", retry(3), breaker(100));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> caller.execute(() -> {
            calls.incrementAndGet();
            throw new TransientRemoteException("falha");
        })).isInstanceOf(TransientRemoteException.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void shouldReturnResultAfterTransientFailures() {
        ResilientCaller caller = new ResilientCaller("retry-recovers", retry(3), breaker(100));
        AtomicInteger calls = new AtomicInteger();

        String result = caller.execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw new TransientRemoteException("falha");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void shouldNotRetryPermanentFailures() {
        ResilientCaller caller = new ResilientCaller("permanent", retry(3), breaker(100));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> caller.execute(() -> {
            calls.incrementAndGet();
            throw new PermanentRemoteException("recusado");
        })).isInstanceOf(PermanentRemoteException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldOpenCircuitAfterTooManyTransientFailures() {
        ResilientCaller caller = new ResilientCaller("opens", retry(1), breaker(4));
        AtomicInteger calls = new AtomicInteger();

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> caller.execute(() -> {
                calls.incrementAndGet();
                throw new TransientRemoteException("falha");
            })).isInstanceOf(TransientRemoteException.class);
        }

        assertThat(caller.isCircuitOpen()).isTrue();
        assertThatThrownBy(() -> caller.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        })).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls).hasValue(4);
    }

    @Test
    void shouldNotCountPermanentFailuresAsCircuitFailures() {
        ResilientCaller caller = new ResilientCaller("permanent-closed", retry(1), breaker(4));

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThatThrownBy(() -> caller.execute(() -> {
                throw new PermanentRemoteException("recusado");
            })).isInstanceOf(PermanentRemoteException.class);
        }

        assertThat(caller.isCircuitOpen()).isFalse();
        assertThat(caller.<String>execute(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void shouldRunActionOnce() {
        ResilientCaller caller = new ResilientCaller("run", retry(3), breaker(100));
        AtomicInteger calls = new AtomicInteger();

        caller.run(calls::incrementAndGet);

        assertThat(calls).hasValue(1);
    }
}