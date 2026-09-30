package com.facegym.domain;

import java.time.Instant;
import java.util.UUID;

/** `vivacidade`: true com prova de vida, false sem ou reprovada, null quando não se aplica (CPF). */
public record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                     MeioIdentificacao meio, Double score, Boolean vivacidade) {

    public Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                  MeioIdentificacao meio, Double score) {
        this(id, dataHora, alunoId, resultado, motivo, meio, score, null);
    }
}
