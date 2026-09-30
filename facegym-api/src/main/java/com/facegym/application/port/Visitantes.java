package com.facegym.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Visitantes {
    /** segredoHash pode ser nulo: o visitante só some quando expirar. */
    void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm, String segredoHash);
    boolean existe(UUID alunoId);
    Optional<String> segredoHash(UUID alunoId);
    List<UUID> expiradosAte(Instant agora);
    long criadosDesde(Instant desde);
}
