package br.com.nevvesdev.alcada.infrastructure.outbox;

public interface OutboxMessagePublisher {

    void publish(OutboxMessage message);
}