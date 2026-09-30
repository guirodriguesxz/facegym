package com.facegym.application.port;

import com.facegym.domain.Acesso;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroDeAcessos {
    void registrar(Acesso acesso);
    long liberadosDesde(UUID alunoId, Instant desde);
    Optional<Instant> ultimoLiberado(UUID alunoId);
    List<Acesso> recentes(int limite);
}
