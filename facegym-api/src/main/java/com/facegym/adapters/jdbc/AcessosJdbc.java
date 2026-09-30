package com.facegym.adapters.jdbc;

import com.facegym.application.port.RegistroDeAcessos;
import com.facegym.domain.Acesso;
import com.facegym.domain.MeioIdentificacao;
import com.facegym.domain.ResultadoAcesso;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AcessosJdbc implements RegistroDeAcessos {
    private static final RowMapper<Acesso> MAPPER = (rs, i) -> new Acesso(
            rs.getObject("id", UUID.class), rs.getTimestamp("data_hora").toInstant(),
            rs.getObject("aluno_id", UUID.class), ResultadoAcesso.valueOf(rs.getString("resultado")),
            rs.getString("motivo"), MeioIdentificacao.valueOf(rs.getString("meio")),
            (Double) rs.getObject("score"), (Boolean) rs.getObject("vivacidade"));

    private final JdbcClient jdbc;

    public AcessosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void registrar(Acesso a) {
        jdbc.sql("INSERT INTO acesso (id, data_hora, aluno_id, resultado, motivo, meio, score, vivacidade) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .param(a.id()).param(Timestamp.from(a.dataHora())).param(a.alunoId()).param(a.resultado().name())
                .param(a.motivo()).param(a.meio().name()).param(a.score()).param(a.vivacidade())
                .update();
    }

    @Override
    public long liberadosDesde(UUID alunoId, Instant desde) {
        return jdbc.sql("SELECT count(*) FROM acesso WHERE aluno_id = ? AND resultado = 'LIBERADO' AND data_hora >= ?")
                .param(alunoId).param(Timestamp.from(desde)).query(Long.class).single();
    }

    @Override
    public Optional<Instant> ultimoLiberado(UUID alunoId) {
        return jdbc.sql("SELECT max(data_hora) FROM acesso WHERE aluno_id = ? AND resultado = 'LIBERADO'")
                .param(alunoId).query(Timestamp.class).optional().map(Timestamp::toInstant);
    }

    @Override
    public List<Acesso> recentes(int limite) {
        return jdbc.sql("SELECT * FROM acesso ORDER BY data_hora DESC LIMIT ?").param(limite).query(MAPPER).list();
    }
}
