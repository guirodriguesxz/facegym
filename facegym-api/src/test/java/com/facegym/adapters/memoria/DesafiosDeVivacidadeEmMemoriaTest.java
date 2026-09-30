package com.facegym.adapters.memoria;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DesafiosDeVivacidadeEmMemoriaTest {

    final DesafiosDeVivacidadeEmMemoria desafios = new DesafiosDeVivacidadeEmMemoria();
    final Instant agora = Instant.parse("2026-10-05T11:00:00Z");

    @Test
    void consumirDevolveOLadoUmaVezSo() {
        var d = desafios.criar(agora.plusSeconds(30));
        assertThat(desafios.consumir(d.token(), agora)).contains(d.lado());
        assertThat(desafios.consumir(d.token(), agora)).isEmpty();
    }

    @Test
    void expiradoOuDesconhecidoOuNuloNaoValem() {
        var d = desafios.criar(agora.plusSeconds(30));
        assertThat(desafios.consumir(d.token(), agora.plusSeconds(31))).isEmpty();
        assertThat(desafios.consumir("inventado", agora)).isEmpty();
        assertThat(desafios.consumir(null, agora)).isEmpty();
    }

    @Test
    void sorteiaOsDoisLados() {
        var lados = java.util.stream.IntStream.range(0, 64)
                .mapToObj(i -> desafios.criar(agora.plusSeconds(30)).lado()).distinct().count();
        assertThat(lados).isEqualTo(2);
    }
}
