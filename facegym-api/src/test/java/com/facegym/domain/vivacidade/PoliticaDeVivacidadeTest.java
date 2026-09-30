package com.facegym.domain.vivacidade;

import org.junit.jupiter.api.Test;

import static com.facegym.domain.vivacidade.LadoDesafio.DIREITA;
import static com.facegym.domain.vivacidade.LadoDesafio.ESQUERDA;
import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeVivacidadeTest {

    final PoliticaDeVivacidade politica = new PoliticaDeVivacidade(new LimiaresDeVivacidade(0.15, 0.25, 0.30));

    MedidasDeVivacidade m(double frente, double virada, double similaridade) {
        return new MedidasDeVivacidade(frente, virada, similaridade);
    }

    @Test
    void aprovaQuemVirouParaOLadoPedido() {
        assertThat(politica.aprova(ESQUERDA, m(0.02, 0.40, 0.70))).isTrue();
        assertThat(politica.aprova(DIREITA, m(-0.02, -0.40, 0.70))).isTrue();
    }

    @Test
    void reprovaLadoErrado() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, -0.40, 0.70))).isFalse();
        assertThat(politica.aprova(DIREITA, m(0.0, 0.40, 0.70))).isFalse();
    }

    @Test
    void limiteDaVirada() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.25, 0.70))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.2499, 0.70))).isFalse();
    }

    @Test
    void frenteTemQueEstarDeFrente() {
        assertThat(politica.aprova(ESQUERDA, m(0.1499, 0.40, 0.70))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.15, 0.40, 0.70))).isFalse();
        assertThat(politica.aprova(ESQUERDA, m(-0.15, 0.40, 0.70))).isFalse();
    }

    @Test
    void viradaEnviadaComoFrenteReprova() {
        // as duas fotos viradas: a "frente" não está de frente
        assertThat(politica.aprova(ESQUERDA, m(0.40, 0.40, 0.95))).isFalse();
    }

    @Test
    void pessoasDiferentesReprova() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.40, 0.30))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.40, 0.2999))).isFalse();
    }
}
