package com.facegym.adapters.jdbc;

import com.facegym.application.NaoEncontrado;
import com.facegym.application.Segredos;
import com.facegym.application.port.Totens;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class TotensJdbc implements Totens {
    public record Totem(UUID id, String nome, Instant criadoEm) {}
    /** O token só existe nesta resposta: depois, nem o admin consegue vê-lo. */
    public record TotemNovo(UUID id, String nome, Instant criadoEm, String token) {}

    private final JdbcClient jdbc;

    public TotensJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** Token de 256 bits: buscar pelo SHA-256 é seguro (não há o que adivinhar byte a byte). */
    @Override
    public boolean autenticado(String token) {
        if (token == null || token.isBlank()) return false;
        return jdbc.sql("SELECT count(*) FROM totem WHERE token_hash = ?").param(Segredos.sha256(token))
                .query(Long.class).single() > 0;
    }

    public TotemNovo criar(String nome, Instant agora) {
        UUID id = UUID.randomUUID();
        String token = Segredos.gerar();
        jdbc.sql("INSERT INTO totem (id, nome, token_hash, criado_em) VALUES (?, ?, ?, ?)")
                .param(id).param(nome).param(Segredos.sha256(token)).param(Timestamp.from(agora)).update();
        return new TotemNovo(id, nome, agora, token);
    }

    public List<Totem> listar() {
        return jdbc.sql("SELECT id, nome, criado_em FROM totem ORDER BY criado_em")
                .query((rs, i) -> new Totem(rs.getObject("id", UUID.class), rs.getString("nome"),
                        rs.getTimestamp("criado_em").toInstant())).list();
    }

    public void remover(UUID id) {
        if (jdbc.sql("DELETE FROM totem WHERE id = ?").param(id).update() == 0) throw new NaoEncontrado("Totem");
    }
}
