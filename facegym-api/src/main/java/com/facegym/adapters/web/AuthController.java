package com.facegym.adapters.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {}

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;

    public AuthController(JdbcClient jdbc, PasswordEncoder encoder, JwtEncoder jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest r) {
        String hash = jdbc.sql("SELECT senha_hash FROM admin WHERE email = ?").param(r.email().toLowerCase())
                .query(String.class).optional().orElse(null);
        if (hash == null || !encoder.matches(r.senha(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }
        Instant agora = Instant.now();
        var claims = JwtClaimsSet.builder().subject(r.email().toLowerCase()).issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofHours(8))).claim("scope", "ADMIN").build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return Map.of("token", jwt.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }
}
