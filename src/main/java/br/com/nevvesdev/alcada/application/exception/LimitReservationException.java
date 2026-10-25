package br.com.nevvesdev.alcada.application.exception;

public class LimitReservationException extends ApplicationException {

    public LimitReservationException(String message) {
        super(message);
    }

    public LimitReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}