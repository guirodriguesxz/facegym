package com.facegym.domain;

import java.time.LocalDate;
import java.util.UUID;

public record Matricula(UUID id, UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento) {
    public Matricula {
        if (vencimento.isBefore(inicio)) throw new IllegalArgumentException("Vencimento antes do início");
    }

    /** Vencimento é inclusivo: vence dia 10, entra dia 10. */
    public boolean vigenteEm(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(vencimento);
    }
}
