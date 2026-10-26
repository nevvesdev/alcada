package br.com.nevvesdev.alcada.infrastructure.limit;

import java.math.BigDecimal;

public record LimitReservationRequest(String requestId, String cpf, BigDecimal amount) {
}