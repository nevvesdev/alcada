package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

public record Cpf(String value) {

    private static final int LENGTH = 11;

    public Cpf {
        if (value == null) {
            throw new DomainValidationException("CPF é obrigatório");
        }
        String digits = value.replaceAll("\\D", "");
        if (!isValid(digits)) {
            throw new DomainValidationException("CPF inválido");
        }
        value = digits;
    }

    public static Cpf of(String value) {
        return new Cpf(value);
    }

    public String masked() {
        return "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
    }

    @Override
    public String toString() {
        return masked();
    }

    private static boolean isValid(String digits) {
        if (digits.length() != LENGTH || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) == digits.charAt(9) - '0'
                && checkDigit(digits, 10) == digits.charAt(10) - '0';
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += (digits.charAt(index) - '0') * (length + 1 - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}