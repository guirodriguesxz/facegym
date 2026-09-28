package com.facegym.domain;

import java.time.Instant;
import java.util.UUID;

public record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                     MeioIdentificacao meio, Double score) {
}
