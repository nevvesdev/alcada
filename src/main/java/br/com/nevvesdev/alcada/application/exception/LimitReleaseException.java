package br.com.nevvesdev.alcada.application.exception;

public class LimitReleaseException extends ApplicationException {

    public LimitReleaseException(String message) {
        super(message);
    }

    public LimitReleaseException(String message, Throwable cause) {
        super(message, cause);
    }
}