package com.facegym.adapters.memoria;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void recusaNovosDesafiosQuandoHaMuitosEmAberto() {
        var pequeno = new DesafiosDeVivacidadeEmMemoria(3);
        Instant daquiAPouco = Instant.now().plusSeconds(30);
        for (int i = 0; i < 3; i++) pequeno.criar(daquiAPouco);
        assertThatThrownBy(() -> pequeno.criar(daquiAPouco)).isInstanceOf(MuitosDesafios.class);
    }

    @Test
    void desafiosJaExpiradosNaoOcupamLugar() {
        var pequeno = new DesafiosDeVivacidadeEmMemoria(3);
        for (int i = 0; i < 3; i++) pequeno.criar(Instant.now().minusSeconds(1));
        pequeno.criar(Instant.now().plusSeconds(30)); // não lança
    }
}
