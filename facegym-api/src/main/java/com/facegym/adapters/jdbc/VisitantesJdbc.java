package com.facegym.adapters.jdbc;

import com.facegym.application.port.Visitantes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class VisitantesJdbc implements Visitantes {
    private final JdbcClient jdbc;

    public VisitantesJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm) {
        jdbc.sql("INSERT INTO visitante (aluno_id, criado_em, expira_em) VALUES (?, ?, ?)")
                .param(alunoId).param(Timestamp.from(criadoEm)).param(Timestamp.from(expiraEm)).update();
        jdbc.sql("INSERT INTO visitante_log (criado_em) VALUES (?)").param(Timestamp.from(criadoEm)).update();
    }

    @Override
    public boolean existe(UUID alunoId) {
        return jdbc.sql("SELECT count(*) FROM visitante WHERE aluno_id = ?").param(alunoId).query(Long.class).single() > 0;
    }

    @Override
    public List<UUID> expiradosAte(Instant agora) {
        return jdbc.sql("SELECT aluno_id FROM visitante WHERE expira_em <= ?").param(Timestamp.from(agora))
                .query(UUID.class).list();
    }

    @Override
    public long criadosDesde(Instant desde) {
        return jdbc.sql("SELECT count(*) FROM visitante_log WHERE criado_em >= ?").param(Timestamp.from(desde))
                .query(Long.class).single();
    }
}
