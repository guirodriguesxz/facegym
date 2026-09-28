package com.facegym.application;

public record Limiares(double aceite, double duvida) {
    public Limiares {
        if (!(duvida < aceite)) throw new IllegalArgumentException("Limiar de dúvida deve ser menor que o de aceite");
    }
}
