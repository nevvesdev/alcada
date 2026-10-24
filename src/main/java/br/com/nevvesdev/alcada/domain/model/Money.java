package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (amount == null) {
            throw new DomainValidationException("Valor monetário é obrigatório");
        }
        if (amount.signum() < 0) {
            throw new DomainValidationException("Valor monetário não pode ser negativo");
        }
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        if (amount == null) {
            throw new DomainValidationException("Valor monetário é obrigatório");
        }
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException exception) {
            throw new DomainValidationException("Valor monetário inválido");
        }
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new DomainValidationException("Fator de multiplicação é obrigatório");
        }
        return new Money(amount.multiply(factor));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}