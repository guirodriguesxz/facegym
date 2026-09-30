package com.facegym.application;

import com.facegym.application.port.Visitantes;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;

import java.util.Set;

/**
 * O que um cliente sem totem registrado (a demo pública) pode alcançar: só dados fictícios,
 * os alunos da galeria (CPFs em FACEGYM_DEMO_CPFS) e os visitantes temporários.
 */
public class Publico {
    private final Set<Cpf> demo;
    private final Visitantes visitantes;

    public Publico(Set<Cpf> demo, Visitantes visitantes) {
        this.demo = Set.copyOf(demo);
        this.visitantes = visitantes;
    }

    /** Aluno da galeria: só tem foto estática, então passa sem prova de vida. */
    public boolean demo(Aluno aluno) { return demo.contains(aluno.cpf()); }

    public boolean contem(Aluno aluno) { return demo(aluno) || visitantes.existe(aluno.id()); }
}
