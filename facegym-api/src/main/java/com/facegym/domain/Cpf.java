package com.facegym.domain;

public record Cpf(String valor) {

    public Cpf {
        if (valor == null || !valor.matches("\\d{11}") || valor.chars().distinct().count() == 1 || !digitosOk(valor)) {
            throw new IllegalArgumentException("CPF inválido");
        }
    }

    public static Cpf of(String entrada) {
        if (entrada == null) throw new IllegalArgumentException("CPF inválido");
        return new Cpf(entrada.replaceAll("\\D", ""));
    }

    private static boolean digitosOk(String c) {
        return digito(c, 9) == c.charAt(9) - '0' && digito(c, 10) == c.charAt(10) - '0';
    }

    private static int digito(String c, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) soma += (c.charAt(i) - '0') * (n + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
