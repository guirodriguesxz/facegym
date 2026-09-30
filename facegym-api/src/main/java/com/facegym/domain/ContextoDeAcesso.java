package com.facegym.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/** Tudo que as regras precisam, já resolvido; `agora` e `ultimaEntrada` estão no fuso da academia. */
public record ContextoDeAcesso(Aluno aluno, Optional<Matricula> matricula, Optional<Plano> plano,
                               LocalDateTime agora, long liberadosNaSemana, Optional<LocalDateTime> ultimaEntrada) {

    public ContextoDeAcesso(Aluno aluno, Optional<Matricula> matricula, Optional<Plano> plano,
                            LocalDateTime agora, long liberadosNaSemana) {
        this(aluno, matricula, plano, agora, liberadosNaSemana, Optional.empty());
    }
}
