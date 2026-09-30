package com.facegym.adapters.biometria;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param janela    chamadas na janela do circuit breaker
 * @param taxaFalha % de falhas que abre o circuito
 * @param espera    tempo aberto antes da meia-abertura
 */
@ConfigurationProperties("facegym.biometria")
public record BiometriaProperties(String url, String chave, Duration timeout, int janela, float taxaFalha,
                                  Duration espera) {
    /** Sem chave padrão: um deploy sem BIOMETRIA_KEY não sobe em vez de usar um segredo publicado. */
    public BiometriaProperties {
        if (chave == null || chave.length() < 16) {
            throw new IllegalStateException("BIOMETRIA_KEY precisa ter pelo menos 16 caracteres");
        }
    }
}
