package com.facegym.domain.vivacidade;

/** `frente`: giro máximo (exclusivo) da foto de frente; `virada` e `mesmaPessoa`: mínimos (inclusivos). */
public record LimiaresDeVivacidade(double frente, double virada, double mesmaPessoa) {
}
