package br.com.nevvesdev.alcada.application.exception;

public class IdempotencyKeyReuseException extends ApplicationException {

    public IdempotencyKeyReuseException() {
        super("Chave de idempotência já utilizada com dados diferentes");
    }
}