package com.facegym.application.port;

import com.facegym.domain.Plano;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Planos {
    Optional<Plano> porId(UUID id);
    void salvar(Plano plano);
    List<Plano> todos();
}
