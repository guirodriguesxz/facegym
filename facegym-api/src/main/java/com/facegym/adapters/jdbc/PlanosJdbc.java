package com.facegym.adapters.jdbc;

import com.facegym.application.port.Planos;
import com.facegym.domain.Plano;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class PlanosJdbc implements Planos {
    private static final RowMapper<Plano> MAPPER = (rs, i) -> new Plano(
            rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBigDecimal("preco"),
            Arrays.stream(rs.getString("dias_semana").split(",")).map(DayOfWeek::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class))),
            rs.getTime("hora_inicio").toLocalTime(), rs.getTime("hora_fim").toLocalTime(),
            (Integer) rs.getObject("acessos_semana"));

    private final JdbcClient jdbc;

    public PlanosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Plano> porId(UUID id) {
        return jdbc.sql("SELECT * FROM plano WHERE id = ?").param(id).query(MAPPER).optional();
    }

    @Override
    public void salvar(Plano p) {
        String dias = p.dias().stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        jdbc.sql("""
                INSERT INTO plano (id, nome, preco, dias_semana, hora_inicio, hora_fim, acessos_semana)
                VALUES (:id, :nome, :preco, :dias, :inicio, :fim, :limite)
                ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, preco = EXCLUDED.preco,
                  dias_semana = EXCLUDED.dias_semana, hora_inicio = EXCLUDED.hora_inicio,
                  hora_fim = EXCLUDED.hora_fim, acessos_semana = EXCLUDED.acessos_semana""")
                .param("id", p.id()).param("nome", p.nome()).param("preco", p.preco()).param("dias", dias)
                .param("inicio", p.inicio()).param("fim", p.fim()).param("limite", p.acessosPorSemana())
                .update();
    }

    @Override
    public List<Plano> todos() {
        return jdbc.sql("SELECT * FROM plano ORDER BY nome").query(MAPPER).list();
    }
}
