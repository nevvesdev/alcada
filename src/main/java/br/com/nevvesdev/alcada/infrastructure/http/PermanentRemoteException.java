package br.com.nevvesdev.alcada.infrastructure.http;

public class PermanentRemoteException extends RemoteCallException {

    public PermanentRemoteException(String message) {
        super(message);
    }

    public PermanentRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}