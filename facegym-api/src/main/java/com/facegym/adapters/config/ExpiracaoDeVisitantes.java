package com.facegym.adapters.config;

import com.facegym.application.VisitantesTemporarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ExpiracaoDeVisitantes {
    private static final Logger log = LoggerFactory.getLogger(ExpiracaoDeVisitantes.class);
    private final VisitantesTemporarios visitantes;

    public ExpiracaoDeVisitantes(VisitantesTemporarios visitantes) { this.visitantes = visitantes; }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirar() {
        int n = visitantes.expirar();
        if (n > 0) log.info("{} visitante(s) temporário(s) apagado(s)", n);
    }
}
