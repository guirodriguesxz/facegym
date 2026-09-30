package com.facegym.adapters.jdbc;

import com.facegym.application.port.Pins;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PinsJdbc implements Pins {
    private final JdbcClient jdbc;

    public PinsJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<String> hash(UUID alunoId) {
        return jdbc.sql("SELECT pin_hash FROM aluno_pin WHERE aluno_id = ?").param(alunoId).query(String.class).optional();
    }

    @Override
    public void definir(UUID alunoId, String hash) {
        jdbc.sql("""
                INSERT INTO aluno_pin (aluno_id, pin_hash) VALUES (?, ?)
                ON CONFLICT (aluno_id) DO UPDATE SET pin_hash = EXCLUDED.pin_hash""")
                .param(alunoId).param(hash).update();
    }
}
