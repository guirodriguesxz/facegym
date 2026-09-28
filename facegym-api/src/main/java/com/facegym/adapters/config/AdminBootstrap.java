package com.facegym.adapters.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Cria o admin inicial a partir de ADMIN_EMAIL/ADMIN_PASSWORD se ainda não existir nenhum. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String senha;

    public AdminBootstrap(JdbcClient jdbc, PasswordEncoder encoder,
                          @Value("${facegym.admin.email}") String email, @Value("${facegym.admin.senha:}") String senha) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
        this.senha = senha;
    }

    /** Sem ADMIN_PASSWORD não cria ninguém: nada de senha padrão conhecida. */
    static boolean deveCriar(long admins, String senha) {
        return admins == 0 && senha != null && !senha.isBlank();
    }

    @Override
    public void run(ApplicationArguments args) {
        long admins = jdbc.sql("SELECT count(*) FROM admin").query(Long.class).single();
        if (deveCriar(admins, senha)) {
            jdbc.sql("INSERT INTO admin (id, email, senha_hash) VALUES (?, ?, ?)")
                    .param(UUID.randomUUID()).param(email.toLowerCase()).param(encoder.encode(senha)).update();
        }
    }
}
