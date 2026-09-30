package com.facegym.adapters.config;

import com.facegym.adapters.biometria.BiometriaHttpClient;
import com.facegym.adapters.biometria.BiometriaProperties;
import com.facegym.adapters.memoria.CheckInsPendentesEmMemoria;
import com.facegym.application.*;
import com.facegym.application.port.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class UseCaseConfig {

    @Bean
    Relogio relogio(@Value("${facegym.fuso}") String fuso) {
        return new RelogioDoSistema(ZoneId.of(fuso));
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    BiometriaHttpClient reconhecimentoFacial(BiometriaProperties props, CircuitBreakerRegistry registry) {
        return new BiometriaHttpClient(props, registry);
    }

    /** Mostra o estado do circuito sem nunca deixar a aplicação DOWN (o Render reiniciaria). */
    @Bean
    HealthIndicator biometriaHealth(BiometriaHttpClient biometria) {
        return () -> {
            CircuitBreaker.State estado = biometria.circuitBreaker().getState();
            return Health.up().withDetail("circuito", estado.name()).build();
        };
    }

    @Bean
    CheckInsPendentes checkInsPendentes() { return new CheckInsPendentesEmMemoria(); }

    @Bean
    RealizarCheckIn realizarCheckIn(ReconhecimentoFacial r, Alunos a, Planos p, Matriculas m, RegistroDeAcessos ac,
                                    CheckInsPendentes pend, Relogio rel,
                                    @Value("${facegym.limiares.aceite}") double aceite,
                                    @Value("${facegym.limiares.duvida}") double duvida,
                                    @Value("${facegym.antipassback}") Duration antipassback) {
        return new RealizarCheckIn(r, a, p, m, ac, pend, rel, new Limiares(aceite, duvida), antipassback);
    }

    @Bean
    GestaoDeAlunos gestaoDeAlunos(Alunos a, ReconhecimentoFacial r, Relogio rel) {
        return new GestaoDeAlunos(a, r, rel);
    }

    @Bean
    GestaoDePlanos gestaoDePlanos(Planos p, Alunos a, Matriculas m) {
        return new GestaoDePlanos(p, a, m);
    }

    @Bean
    VisitantesTemporarios visitantesTemporarios(Alunos a, Matriculas m, Visitantes v, ReconhecimentoFacial r, Relogio rel) {
        return new VisitantesTemporarios(a, m, v, r, rel);
    }
}
