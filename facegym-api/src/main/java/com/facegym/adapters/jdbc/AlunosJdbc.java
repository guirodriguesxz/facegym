package com.facegym.adapters.jdbc;

import com.facegym.application.port.Alunos;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AlunosJdbc implements Alunos {
    private static final RowMapper<Aluno> MAPPER = (rs, i) -> {
        Timestamp consentimento = rs.getTimestamp("consentimento_biometrico_em");
        return new Aluno(rs.getObject("id", UUID.class), rs.getString("nome"), new Cpf(rs.getString("cpf")),
                rs.getString("email"), rs.getBoolean("bloqueado"), rs.getString("motivo_bloqueio"),
                consentimento == null ? null : consentimento.toInstant());
    };

    private final JdbcClient jdbc;

    public AlunosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Aluno> porId(UUID id) {
        return jdbc.sql("SELECT * FROM aluno WHERE id = ?").param(id).query(MAPPER).optional();
    }

    @Override
    public Optional<Aluno> porCpf(Cpf cpf) {
        return jdbc.sql("SELECT * FROM aluno WHERE cpf = ?").param(cpf.valor()).query(MAPPER).optional();
    }

    @Override
    public void salvar(Aluno a) {
        jdbc.sql("""
                INSERT INTO aluno (id, nome, cpf, email, bloqueado, motivo_bloqueio, consentimento_biometrico_em)
                VALUES (:id, :nome, :cpf, :email, :bloqueado, :motivo, :consentimento)
                ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, email = EXCLUDED.email,
                  bloqueado = EXCLUDED.bloqueado, motivo_bloqueio = EXCLUDED.motivo_bloqueio,
                  consentimento_biometrico_em = EXCLUDED.consentimento_biometrico_em""")
                .param("id", a.id()).param("nome", a.nome()).param("cpf", a.cpf().valor()).param("email", a.email())
                .param("bloqueado", a.bloqueado()).param("motivo", a.motivoBloqueio())
                .param("consentimento", a.consentimentoBiometricoEm() == null ? null : Timestamp.from(a.consentimentoBiometricoEm()))
                .update();
    }

    @Override
    public List<Aluno> todos() {
        return jdbc.sql("SELECT * FROM aluno ORDER BY nome").query(MAPPER).list();
    }

    @Override
    public void remover(UUID id) {
        jdbc.sql("DELETE FROM aluno WHERE id = ?").param(id).update();
    }
}
