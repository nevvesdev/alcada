package br.com.nevvesdev.alcada.domain.exception;

public class DomainValidationException extends DomainException {

    public DomainValidationException(String message) {
        super(message);
    }
}