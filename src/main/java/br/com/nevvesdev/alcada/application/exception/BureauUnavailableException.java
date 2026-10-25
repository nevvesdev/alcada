package br.com.nevvesdev.alcada.application.exception;

public class BureauUnavailableException extends ApplicationException {

    public BureauUnavailableException(String message) {
        super(message);
    }

    public BureauUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}