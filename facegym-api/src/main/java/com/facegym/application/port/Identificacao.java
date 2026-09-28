package com.facegym.application.port;

import java.util.UUID;

public record Identificacao(UUID alunoId, Double score) {
    public static Identificacao ninguem() { return new Identificacao(null, null); }
    public boolean encontrou() { return alunoId != null && score != null; }
}
