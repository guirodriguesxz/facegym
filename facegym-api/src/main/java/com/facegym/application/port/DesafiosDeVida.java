package com.facegym.application.port;

import java.time.Instant;
import java.util.Optional;

/** Desafios de prova de vida emitidos ao totem: uso único, vida curta. */
public interface DesafiosDeVida {
    enum Direcao { ESQUERDA, DIREITA }

    String criar(Direcao direcao, Instant expiraEm);
    /** Remove e devolve; vazio se não existe ou expirou. */
    Optional<Direcao> consumir(String id, Instant agora);
}
