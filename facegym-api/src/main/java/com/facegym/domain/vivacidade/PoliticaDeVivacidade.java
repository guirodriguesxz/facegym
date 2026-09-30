package com.facegym.domain.vivacidade;

/**
 * Prova de vida por desafio: a primeira foto está de frente, a segunda virou para o lado sorteado
 * e as duas são da mesma pessoa. Uma foto parada não vira; um vídeo gravado não sabe o lado.
 */
public class PoliticaDeVivacidade {
    private final LimiaresDeVivacidade limiares;

    public PoliticaDeVivacidade(LimiaresDeVivacidade limiares) { this.limiares = limiares; }

    public boolean aprova(LadoDesafio lado, MedidasDeVivacidade m) {
        double giroNoLado = lado == LadoDesafio.ESQUERDA ? m.giroVirada() : -m.giroVirada();
        return Math.abs(m.giroFrente()) < limiares.frente()
                && giroNoLado >= limiares.virada()
                && m.similaridade() >= limiares.mesmaPessoa();
    }
}
