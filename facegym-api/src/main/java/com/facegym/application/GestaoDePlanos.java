package com.facegym.application;

import com.facegym.application.port.Alunos;
import com.facegym.application.port.Matriculas;
import com.facegym.application.port.Planos;
import com.facegym.domain.Matricula;
import com.facegym.domain.Plano;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GestaoDePlanos {
    private final Planos planos;
    private final Alunos alunos;
    private final Matriculas matriculas;

    public GestaoDePlanos(Planos planos, Alunos alunos, Matriculas matriculas) {
        this.planos = planos;
        this.alunos = alunos;
        this.matriculas = matriculas;
    }

    public Plano cadastrar(String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim,
                           Integer acessosPorSemana) {
        Plano p = new Plano(UUID.randomUUID(), nome, preco, dias, inicio, fim, acessosPorSemana);
        planos.salvar(p);
        return p;
    }

    public List<Plano> listar() { return planos.todos(); }

    public Matricula matricular(UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento) {
        alunos.porId(alunoId).orElseThrow(() -> new NaoEncontrado("Aluno"));
        planos.porId(planoId).orElseThrow(() -> new NaoEncontrado("Plano"));
        Matricula m = new Matricula(UUID.randomUUID(), alunoId, planoId, inicio, vencimento);
        matriculas.salvar(m);
        return m;
    }
}
