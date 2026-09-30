package com.facegym.adapters.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeTentativasTest {
    private final LimiteDeTentativas limite = new LimiteDeTentativas();

    @Test
    void bloqueiaDepoisDoMaximoELiberaNaProximaJanela() {
        var regra = new LimiteDeTentativas.Regra("cpf", 3, LimiteDeTentativas.MINUTO_MS);
        for (int i = 0; i < 3; i++) assertThat(limite.permitir("cpf|1.1.1.1", regra, 1_000)).isTrue();
        assertThat(limite.permitir("cpf|1.1.1.1", regra, 2_000)).isFalse();
        assertThat(limite.permitir("cpf|2.2.2.2", regra, 2_000)).isTrue(); // outro IP tem a própria cota
        assertThat(limite.permitir("cpf|1.1.1.1", regra, 1_000 + LimiteDeTentativas.MINUTO_MS)).isTrue();
    }

    @Test
    void soAsRotasPublicasSensiveisSaoLimitadas() {
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/check-ins/cpf")).isNotNull();
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/check-ins/abc/cpf")).isNotNull();
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/check-ins")).isNotNull();
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/auth/login")).isNotNull();
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/demo/aquecer")).isNotNull();
        assertThat(LimiteDeTentativas.regra("POST", "/api/v1/check-ins/desafio").nome()).isEqualTo("desafio");
        assertThat(LimiteDeTentativas.regra("GET", "/api/v1/alunos")).isNull();
        assertThat(LimiteDeTentativas.regra("DELETE", "/api/v1/demo/visitantes/x")).isNull();
    }

    @Test
    void visitanteTemCotaPorHora() {
        var regra = LimiteDeTentativas.regra("POST", "/api/v1/demo/visitantes");
        assertThat(regra.janelaMs()).isEqualTo(LimiteDeTentativas.HORA_MS);
        for (int i = 0; i < regra.maximo(); i++) assertThat(limite.permitir("v|1.1.1.1", regra, 0)).isTrue();
        assertThat(limite.permitir("v|1.1.1.1", regra, 30 * LimiteDeTentativas.MINUTO_MS)).isFalse();
        assertThat(limite.permitir("v|1.1.1.1", regra, LimiteDeTentativas.HORA_MS)).isTrue();
    }

    @Test
    void entradasDeJanelaCurtaSaemQuandoVencem() {
        var minuto = new LimiteDeTentativas.Regra("cpf", 3, LimiteDeTentativas.MINUTO_MS);
        for (int i = 0; i < 500; i++) limite.permitir("cpf|10.0.0." + i, minuto, 0);
        assertThat(limite.chaves()).isEqualTo(500);
        limite.permitir("cpf|outro", minuto, LimiteDeTentativas.MINUTO_MS);
        assertThat(limite.chaves()).isEqualTo(1);
    }

    @Test
    void mapaCheioRecusaChavesNovasMasNaoAsExistentes() {
        var hora = new LimiteDeTentativas.Regra("visitante", 3, LimiteDeTentativas.HORA_MS);
        for (int i = 0; i < LimiteDeTentativas.MAX_CHAVES; i++) limite.permitir("v|" + i, hora, 0);
        assertThat(limite.permitir("v|novo", hora, 10)).isFalse();
        assertThat(limite.permitir("v|0", hora, 10)).isTrue();
        assertThat(limite.chaves()).isEqualTo(LimiteDeTentativas.MAX_CHAVES);
    }

    @Test
    void urlCodificadaNaoEscapaDoLimite() throws Exception {
        var req = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/%61uth/login");
        req.setServletPath("/api/v1/auth/login"); // o que o Tomcat entrega: decodificado
        req.setRemoteAddr("9.9.9.9");
        int bloqueadas = 0;
        for (int i = 0; i < 11; i++) {
            var res = new org.springframework.mock.web.MockHttpServletResponse();
            limite.doFilter(req, res, new org.springframework.mock.web.MockFilterChain());
            if (res.getStatus() == 429) bloqueadas++;
        }
        assertThat(bloqueadas).isEqualTo(1);
    }
}
