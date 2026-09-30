package com.facegym.application.port;

import java.util.UUID;

/** vivo: passou pela prova de vida (duas fotos, rosto girado). Foto única nunca é viva. */
public record Identificacao(UUID alunoId, Double score, boolean vivo) {
    public Identificacao(UUID alunoId, Double score) { this(alunoId, score, false); }
    public static Identificacao ninguem() { return new Identificacao(null, null); }
    public boolean encontrou() { return alunoId != null && score != null; }
}
