package br.com.nevvesdev.alcada.domain.exception;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}