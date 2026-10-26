package br.com.nevvesdev.alcada.infrastructure.limit;

import br.com.nevvesdev.alcada.application.exception.LimitConfirmationException;
import br.com.nevvesdev.alcada.application.exception.LimitReleaseException;
import br.com.nevvesdev.alcada.application.exception.LimitReservationException;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteCallException;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteCalls;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteServiceProperties;
import br.com.nevvesdev.alcada.infrastructure.http.ResilientCaller;
import br.com.nevvesdev.alcada.infrastructure.http.RestClients;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public final class HttpLimitReservationAdapter implements LimitReservationPort {

    private static final String RESERVATIONS_PATH = "/limits/reservations";
    private static final String RESERVATION_PATH = "/limits/reservations/{requestId}";
    private static final String CONFIRMATION_PATH = "/limits/reservations/{requestId}/confirm";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final ResilientCaller caller;

    public HttpLimitReservationAdapter(RemoteServiceProperties properties) {
        this.restClient = RestClients.create(properties);
        this.caller = new ResilientCaller("limits", properties.retry(), properties.circuitBreaker());
    }

    @Override
    public void reserve(CreditRequestId requestId, Cpf cpf, Money amount) {
        try {
            caller.run(() -> RemoteCalls.translateAction(() -> restClient.post()
                    .uri(RESERVATIONS_PATH)
                    .header(IDEMPOTENCY_KEY_HEADER, requestId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LimitReservationRequest(requestId.toString(), cpf.value(), amount.amount()))
                    .retrieve()
                    .toBodilessEntity()));
        } catch (RemoteCallException | CallNotPermittedException exception) {
            throw new LimitReservationException("Falha ao reservar limite", exception);
        }
    }

    @Override
    public void confirm(CreditRequestId requestId) {
        try {
            caller.run(() -> RemoteCalls.translateAction(() -> restClient.post()
                    .uri(CONFIRMATION_PATH, requestId.toString())
                    .header(IDEMPOTENCY_KEY_HEADER, requestId.toString())
                    .retrieve()
                    .toBodilessEntity()));
        } catch (RemoteCallException | CallNotPermittedException exception) {
            throw new LimitConfirmationException("Falha ao confirmar limite", exception);
        }
    }

    @Override
    public void release(CreditRequestId requestId) {
        try {
            caller.run(() -> RemoteCalls.translateAction(() -> deleteReservation(requestId)));
        } catch (RemoteCallException | CallNotPermittedException exception) {
            throw new LimitReleaseException("Falha ao liberar reserva de limite", exception);
        }
    }

    private void deleteReservation(CreditRequestId requestId) {
        try {
            restClient.delete()
                    .uri(RESERVATION_PATH, requestId.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
        }
    }
}