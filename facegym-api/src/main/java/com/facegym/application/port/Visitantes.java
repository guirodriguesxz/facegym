package com.facegym.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface Visitantes {
    void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm);
    boolean existe(UUID alunoId);
    List<UUID> expiradosAte(Instant agora);
    long criadosDesde(Instant desde);
}
