package com.facegym.application.port;

import com.facegym.domain.vivacidade.MedidasDeVivacidade;

import java.util.Optional;
import java.util.UUID;

/** Resposta da biometria para frente + virada; medidas nulas quando falta rosto numa das fotos. */
public record IdentificacaoComVivacidade(UUID alunoId, Double score, Double giroFrente, Double giroVirada,
                                         Double similaridade) {

    public Identificacao identificacao() { return new Identificacao(alunoId, score); }

    public Optional<MedidasDeVivacidade> medidas() {
        if (giroFrente == null || giroVirada == null || similaridade == null) return Optional.empty();
        return Optional.of(new MedidasDeVivacidade(giroFrente, giroVirada, similaridade));
    }
}
