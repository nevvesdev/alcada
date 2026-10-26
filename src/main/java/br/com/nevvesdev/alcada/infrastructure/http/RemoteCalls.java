package br.com.nevvesdev.alcada.infrastructure.http;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

public final class RemoteCalls {

    private static final int REQUEST_TIMEOUT = 408;
    private static final int TOO_MANY_REQUESTS = 429;

    private RemoteCalls() {
    }

    public static <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpServerErrorException exception) {
            throw new TransientRemoteException(
                    "Serviço remoto respondeu com status " + exception.getStatusCode().value(), exception);
        } catch (HttpClientErrorException exception) {
            int status = exception.getStatusCode().value();
            String message = "Serviço remoto respondeu com status " + status;
            if (status == REQUEST_TIMEOUT || status == TOO_MANY_REQUESTS) {
                throw new TransientRemoteException(message, exception);
            }
            throw new PermanentRemoteException(message, exception);
        } catch (ResourceAccessException exception) {
            throw new TransientRemoteException("Falha de comunicação com o serviço remoto", exception);
        } catch (RestClientException exception) {
            throw new PermanentRemoteException("Resposta inválida do serviço remoto", exception);
        }
    }

    public static void translateAction(Runnable call) {
        translate(() -> {
            call.run();
            return null;
        });
    }
}