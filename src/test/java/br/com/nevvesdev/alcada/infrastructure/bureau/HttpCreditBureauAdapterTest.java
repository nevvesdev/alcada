package br.com.nevvesdev.alcada.infrastructure.bureau;

import br.com.nevvesdev.alcada.application.exception.BureauUnavailableException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.infrastructure.http.CircuitBreakerProperties;
import br.com.nevvesdev.alcada.infrastructure.http.PermanentRemoteException;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteServiceProperties;
import br.com.nevvesdev.alcada.infrastructure.http.RetryProperties;
import br.com.nevvesdev.alcada.infrastructure.http.TransientRemoteException;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer.RecordedRequest;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer.Response;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCreditBureauAdapterTest {

    private static final Cpf CPF = Cpf.of("529.982.247-25");
    private static final String PATH = "/bureau/scores";

    private final StubHttpServer server = new StubHttpServer();

    @AfterEach
    void stopServer() {
        server.close();
    }

    private static RemoteServiceProperties properties(String baseUrl,
                                                      Duration readTimeout,
                                                      int maxAttempts,
                                                      CircuitBreakerProperties breaker) {
        return new RemoteServiceProperties(
                baseUrl,
                Duration.ofSeconds(5),
                readTimeout,
                new RetryProperties(maxAttempts, Duration.ofMillis(1), 2.0),
                breaker);
    }

    private static CircuitBreakerProperties relaxedBreaker() {
        return new CircuitBreakerProperties(50f, 100, 100, Duration.ofSeconds(30), 1);
    }

    private HttpCreditBureauAdapter adapter(int maxAttempts) {
        return new HttpCreditBureauAdapter(
                properties(server.baseUrl(), Duration.ofSeconds(5), maxAttempts, relaxedBreaker()));
    }

    @Test
    void shouldReturnScoreFromBureau() {
        server.respond("POST", PATH, Response.json(200, "{\"score\":720}"));

        CreditScore score = adapter(3).fetchScore(CPF);

        assertThat(score).isEqualTo(CreditScore.of(720));
        RecordedRequest request = server.requests().get(0);
        assertThat(request.header("Content-Type")).startsWith("application/json");
        assertThat(request.body()).contains("52998224725");
    }

    @Test
    void shouldRetryTransientFailureAndSucceed() {
        server.on("POST", PATH, (request, call) ->
                call <= 2 ? Response.empty(503) : Response.json(200, "{\"score\":650}"));

        CreditScore score = adapter(3).fetchScore(CPF);

        assertThat(score).isEqualTo(CreditScore.of(650));
        assertThat(server.requestCount()).isEqualTo(3);
    }

    @Test
    void shouldFailAfterRetriesAreExhausted() {
        server.respond("POST", PATH, Response.empty(500));

        assertThatThrownBy(() -> adapter(3).fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryClientErrors() {
        server.respond("POST", PATH, Response.empty(422));

        assertThatThrownBy(() -> adapter(3).fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(PermanentRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryWhenBureauIsTooSlow() {
        server.respond("POST", PATH, Response.delayed(200, "{\"score\":720}", Duration.ofMillis(800)));
        HttpCreditBureauAdapter slowAdapter = new HttpCreditBureauAdapter(
                properties(server.baseUrl(), Duration.ofMillis(200), 2, relaxedBreaker()));

        assertThatThrownBy(() -> slowAdapter.fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectScoreOutsideValidRange() {
        server.respond("POST", PATH, Response.json(200, "{\"score\":1500}"));

        assertThatThrownBy(() -> adapter(3).fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(PermanentRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectResponseWithoutScore() {
        server.respond("POST", PATH, Response.json(200, "{}"));

        assertThatThrownBy(() -> adapter(3).fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(PermanentRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldFailFastWhenCircuitIsOpen() {
        server.respond("POST", PATH, Response.empty(500));
        HttpCreditBureauAdapter adapter = new HttpCreditBureauAdapter(properties(
                server.baseUrl(),
                Duration.ofSeconds(5),
                1,
                new CircuitBreakerProperties(50f, 4, 4, Duration.ofSeconds(30), 1)));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> adapter.fetchScore(CPF))
                    .isInstanceOf(BureauUnavailableException.class)
                    .hasCauseInstanceOf(TransientRemoteException.class);
        }

        assertThatThrownBy(() -> adapter.fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        assertThat(server.requestCount()).isEqualTo(4);
    }

    @Test
    void shouldFailWhenConnectionIsRefused() {
        String closedUrl;
        try (StubHttpServer closed = new StubHttpServer()) {
            closedUrl = closed.baseUrl();
        }
        HttpCreditBureauAdapter adapter = new HttpCreditBureauAdapter(
                properties(closedUrl, Duration.ofSeconds(5), 2, relaxedBreaker()));

        assertThatThrownBy(() -> adapter.fetchScore(CPF))
                .isInstanceOf(BureauUnavailableException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
    }
}