package br.com.nevvesdev.alcada.infrastructure.limit;

import br.com.nevvesdev.alcada.application.exception.LimitConfirmationException;
import br.com.nevvesdev.alcada.application.exception.LimitReleaseException;
import br.com.nevvesdev.alcada.application.exception.LimitReservationException;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.infrastructure.http.CircuitBreakerProperties;
import br.com.nevvesdev.alcada.infrastructure.http.PermanentRemoteException;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteServiceProperties;
import br.com.nevvesdev.alcada.infrastructure.http.RetryProperties;
import br.com.nevvesdev.alcada.infrastructure.http.TransientRemoteException;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer.RecordedRequest;
import br.com.nevvesdev.alcada.infrastructure.support.StubHttpServer.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLimitReservationAdapterTest {

    private static final Cpf CPF = Cpf.of("529.982.247-25");
    private static final Money AMOUNT = Money.of("5000");
    private static final String RESERVATIONS_PATH = "/limits/reservations";

    private final StubHttpServer server = new StubHttpServer();
    private final CreditRequestId requestId = CreditRequestId.generate();

    @AfterEach
    void stopServer() {
        server.close();
    }

    private HttpLimitReservationAdapter adapter() {
        return new HttpLimitReservationAdapter(new RemoteServiceProperties(
                server.baseUrl(),
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                new RetryProperties(3, Duration.ofMillis(1), 2.0),
                new CircuitBreakerProperties(50f, 100, 100, Duration.ofSeconds(30), 1)));
    }

    private String confirmationPath() {
        return RESERVATIONS_PATH + "/" + requestId + "/confirm";
    }

    private String reservationPath() {
        return RESERVATIONS_PATH + "/" + requestId;
    }

    @Test
    void shouldReserveLimitSendingIdempotencyKey() {
        server.respond("POST", RESERVATIONS_PATH, Response.json(201, "{}"));

        adapter().reserve(requestId, CPF, AMOUNT);

        RecordedRequest request = server.requests().get(0);
        assertThat(request.header("Idempotency-Key")).isEqualTo(requestId.toString());
        assertThat(request.header("Content-Type")).startsWith("application/json");
        assertThat(request.body()).contains(requestId.toString(), "52998224725", "amount");
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryTransientFailureWhenReserving() {
        server.on("POST", RESERVATIONS_PATH, (request, call) ->
                call == 1 ? Response.empty(500) : Response.json(201, "{}"));

        assertThatCode(() -> adapter().reserve(requestId, CPF, AMOUNT)).doesNotThrowAnyException();
        assertThat(server.requestCount()).isEqualTo(2);
    }

    @Test
    void shouldNotRetryBusinessRefusalWhenReserving() {
        server.respond("POST", RESERVATIONS_PATH, Response.empty(422));

        assertThatThrownBy(() -> adapter().reserve(requestId, CPF, AMOUNT))
                .isInstanceOf(LimitReservationException.class)
                .hasCauseInstanceOf(PermanentRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldFailReservationAfterRetriesAreExhausted() {
        server.respond("POST", RESERVATIONS_PATH, Response.empty(500));

        assertThatThrownBy(() -> adapter().reserve(requestId, CPF, AMOUNT))
                .isInstanceOf(LimitReservationException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(3);
    }

    @Test
    void shouldConfirmReservationSendingIdempotencyKey() {
        server.respond("POST", confirmationPath(), Response.empty(200));

        adapter().confirm(requestId);

        assertThat(server.requests().get(0).header("Idempotency-Key")).isEqualTo(requestId.toString());
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldFailConfirmationAfterRetriesAreExhausted() {
        server.respond("POST", confirmationPath(), Response.empty(500));

        assertThatThrownBy(() -> adapter().confirm(requestId))
                .isInstanceOf(LimitConfirmationException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(3);
    }

    @Test
    void shouldReleaseReservation() {
        server.respond("DELETE", reservationPath(), Response.empty(204));

        assertThatCode(() -> adapter().release(requestId)).doesNotThrowAnyException();
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldTreatMissingReservationAsReleased() {
        server.respond("DELETE", reservationPath(), Response.empty(404));

        assertThatCode(() -> adapter().release(requestId)).doesNotThrowAnyException();
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void shouldFailReleaseAfterRetriesAreExhausted() {
        server.respond("DELETE", reservationPath(), Response.empty(500));

        assertThatThrownBy(() -> adapter().release(requestId))
                .isInstanceOf(LimitReleaseException.class)
                .hasCauseInstanceOf(TransientRemoteException.class);
        assertThat(server.requestCount()).isEqualTo(3);
    }
}