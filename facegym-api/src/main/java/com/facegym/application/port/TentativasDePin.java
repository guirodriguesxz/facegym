package com.facegym.application.port;

import java.time.Instant;
import java.util.UUID;

/** Conta PINs errados por aluno para bloquear a adivinhação. */
public interface TentativasDePin {
    boolean bloqueado(UUID alunoId, Instant agora);
    void falhou(UUID alunoId, Instant agora);
    void limpar(UUID alunoId);
}
