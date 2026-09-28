package com.facegym.domain;

public sealed interface Decisao {
    record Libera() implements Decisao {}
    record Nega(String motivo) implements Decisao {}

    static Decisao libera() { return new Libera(); }
    static Decisao nega(String motivo) { return new Nega(motivo); }
}
