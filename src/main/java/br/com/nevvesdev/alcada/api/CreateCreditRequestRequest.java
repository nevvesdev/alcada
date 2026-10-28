package br.com.nevvesdev.alcada.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateCreditRequestRequest(
        @NotBlank(message = "é obrigatório") String cpf,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero")
        BigDecimal requestedAmount,
        @NotNull(message = "é obrigatório") @PositiveOrZero(message = "não pode ser negativo")
        BigDecimal declaredIncome) {
}