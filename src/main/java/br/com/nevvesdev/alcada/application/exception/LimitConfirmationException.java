package br.com.nevvesdev.alcada.application.exception;

public class LimitConfirmationException extends ApplicationException {

    public LimitConfirmationException(String message) {
        super(message);
    }

    public LimitConfirmationException(String message, Throwable cause) {
        super(message, cause);
    }
}