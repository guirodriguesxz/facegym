package com.facegym.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckInsPendentes {
    String criar(UUID alunoId, Double score, Instant expiraEm);
    /** Remove e devolve; vazio se não existe ou expirou. Uso único. */
    Optional<Pendente> consumir(String token, Instant agora);

    record Pendente(UUID alunoId, Double score) {}
}
