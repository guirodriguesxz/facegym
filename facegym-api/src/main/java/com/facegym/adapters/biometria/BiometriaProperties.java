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
}
