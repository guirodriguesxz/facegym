package com.facegym.application.port;

/** Totens físicos cadastrados pelo admin. Só eles alcançam alunos reais. */
public interface Totens {
    boolean autenticado(String token);
}
