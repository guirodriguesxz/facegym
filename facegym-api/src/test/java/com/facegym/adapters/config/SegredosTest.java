package com.facegym.adapters.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegredosTest {

    final SecurityConfig config = new SecurityConfig();

    @Test
    void semJwtSecretGeraChaveAleatoria() {
        var a = config.jwtKey("");
        var b = config.jwtKey("");
        assertThat(a.getEncoded()).hasSize(32);
        assertThat(a.getEncoded()).isNotEqualTo(b.getEncoded());
    }

    @Test
    void jwtSecretCurtoFalha() {
        assertThatThrownBy(() -> config.jwtKey("curto")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminSoECriadoComSenhaDefinidaEBancoVazio() {
        assertThat(AdminBootstrap.deveCriar(0, "")).isFalse();
        assertThat(AdminBootstrap.deveCriar(0, null)).isFalse();
        assertThat(AdminBootstrap.deveCriar(1, "senha-forte-123")).isFalse();
        assertThat(AdminBootstrap.deveCriar(0, "senha-forte-123")).isTrue();
    }
}
