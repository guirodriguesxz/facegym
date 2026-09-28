package com.facegym.adapters.jdbc;

import com.facegym.application.port.Matriculas;
import com.facegym.domain.Matricula;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MatriculasJdbc implements Matriculas {
    private static final RowMapper<Matricula> MAPPER = (rs, i) -> new Matricula(
            rs.getObject("id", UUID.class), rs.getObject("aluno_id", UUID.class), rs.getObject("plano_id", UUID.class),
            rs.getObject("inicio", LocalDate.class), rs.getObject("vencimento", LocalDate.class));

    private final JdbcClient jdbc;

    public MatriculasJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Matricula> vigente(UUID alunoId, LocalDate dia) {
        return jdbc.sql("""
                SELECT * FROM matricula WHERE aluno_id = ? AND inicio <= ? AND vencimento >= ?
                ORDER BY vencimento DESC LIMIT 1""")
                .param(alunoId).param(dia).param(dia).query(MAPPER).optional();
    }

    @Override
    public void salvar(Matricula m) {
        jdbc.sql("INSERT INTO matricula (id, aluno_id, plano_id, inicio, vencimento) VALUES (?, ?, ?, ?, ?)")
                .param(m.id()).param(m.alunoId()).param(m.planoId()).param(m.inicio()).param(m.vencimento())
                .update();
    }
}
