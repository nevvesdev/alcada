package br.com.nevvesdev.alcada.infrastructure.http;

public class TransientRemoteException extends RemoteCallException {

    public TransientRemoteException(String message) {
        super(message);
    }

    public TransientRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}