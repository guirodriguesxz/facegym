package com.facegym.application.port;

import com.facegym.domain.vivacidade.LadoDesafio;

import java.time.Instant;
import java.util.Optional;

public interface DesafiosDeVivacidade {
    /** Sorteia o lado e guarda até `expiraEm`. */
    Desafio criar(Instant expiraEm);
    /** Remove e devolve o lado; vazio se não existe ou expirou. Uso único. */
    Optional<LadoDesafio> consumir(String token, Instant agora);

    record Desafio(String token, LadoDesafio lado) {}
}
