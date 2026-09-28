package com.facegym.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/** Tudo que as regras precisam, já resolvido; `agora` está no fuso da academia. */
public record ContextoDeAcesso(Aluno aluno, Optional<Matricula> matricula, Optional<Plano> plano,
                               LocalDateTime agora, long liberadosNaSemana) {
}
