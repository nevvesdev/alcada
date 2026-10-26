package br.com.nevvesdev.alcada.infrastructure.http;

public abstract class RemoteCallException extends RuntimeException {

    protected RemoteCallException(String message) {
        super(message);
    }

    protected RemoteCallException(String message, Throwable cause) {
        super(message, cause);
    }
}