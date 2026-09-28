package com.facegym.application.port;

import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Alunos {
    Optional<Aluno> porId(UUID id);
    Optional<Aluno> porCpf(Cpf cpf);
    void salvar(Aluno aluno);
    List<Aluno> todos();
}
