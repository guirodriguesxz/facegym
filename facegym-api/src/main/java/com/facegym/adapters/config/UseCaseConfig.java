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
    CofreDePin cofreDePin(org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return new CofreDePin() {
            public String gerarHash(String pin) { return encoder.encode(pin); }
            public boolean confere(String pin, String hash) { return encoder.matches(pin, hash); }
        };
    }

    @Bean
    TentativasDePin tentativasDePin() { return new com.facegym.adapters.memoria.TentativasDePinEmMemoria(); }

    @Bean
    PinDoAluno pinDoAluno(Pins pins, CofreDePin cofre, TentativasDePin tentativas, Alunos alunos) {
        return new PinDoAluno(pins, cofre, tentativas, alunos);
    }

    @Bean
    DesafiosDeVida desafiosDeVida() { return new com.facegym.adapters.memoria.DesafiosDeVidaEmMemoria(); }

    @Bean
    RealizarCheckIn realizarCheckIn(ReconhecimentoFacial r, Alunos a, Planos p, Matriculas m, RegistroDeAcessos ac,
                                    CheckInsPendentes pend, Relogio rel, DesafiosDeVida desafios, Visitantes visitantes,
                                    PinDoAluno pin,
                                    @Value("${facegym.limiares.aceite}") double aceite,
                                    @Value("${facegym.limiares.duvida}") double duvida,
                                    @Value("${facegym.demo-cpfs:}") String demoCpfs) {
        var isentos = java.util.Arrays.stream(demoCpfs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(com.facegym.domain.Cpf::of).collect(java.util.stream.Collectors.toSet());
        return new RealizarCheckIn(r, a, p, m, ac, pend, rel, new Limiares(aceite, duvida),
                new Publico(isentos, visitantes), pin, desafios);
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
