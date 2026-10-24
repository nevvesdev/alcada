package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

public record CreditScore(int value) implements Comparable<CreditScore> {

    public static final int MIN = 0;
    public static final int MAX = 1000;

    public CreditScore {
        if (value < MIN || value > MAX) {
            throw new DomainValidationException("Score deve estar entre " + MIN + " e " + MAX);
        }
    }

    public static CreditScore of(int value) {
        return new CreditScore(value);
    }

    public boolean isLowerThan(CreditScore other) {
        return value < other.value;
    }

    @Override
    public int compareTo(CreditScore other) {
        return Integer.compare(value, other.value);
    }
}