package com.facegym.adapters.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeRequisicoesTest {
    @Test
    void bloqueiaAposOLimiteELiberaNaProximaJanela() {
        var agora = new AtomicLong(0);
        var filtro = new LimiteDeRequisicoes(5, 10, 30, agora::get);

        for (int i = 0; i < 5; i++) assertThat(filtro.permitir("login|1.1.1.1", 5)).isTrue();
        assertThat(filtro.permitir("login|1.1.1.1", 5)).isFalse();
        assertThat(filtro.permitir("login|2.2.2.2", 5)).as("outro IP não é afetado").isTrue();

        agora.set(60_000);
        assertThat(filtro.permitir("login|1.1.1.1", 5)).isTrue();
    }
}
