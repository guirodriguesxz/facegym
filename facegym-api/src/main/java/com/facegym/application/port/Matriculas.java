package com.facegym.application.port;

import com.facegym.domain.Matricula;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface Matriculas {
    Optional<Matricula> vigente(UUID alunoId, LocalDate dia);
    void salvar(Matricula matricula);
}
