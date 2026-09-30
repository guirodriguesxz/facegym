package com.facegym.application.port;

import java.util.Optional;
import java.util.UUID;

/** Hash do PIN de cada aluno, segundo fator do check-in só por CPF. */
public interface Pins {
    Optional<String> hash(UUID alunoId);
    void definir(UUID alunoId, String hash);
}
