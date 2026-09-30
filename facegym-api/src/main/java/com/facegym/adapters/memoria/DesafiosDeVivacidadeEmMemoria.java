package com.facegym.adapters.memoria;

import com.facegym.application.port.DesafiosDeVivacidade;
import com.facegym.domain.vivacidade.LadoDesafio;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Desafios vivem 30 s; memória basta (instância única). */
public class DesafiosDeVivacidadeEmMemoria implements DesafiosDeVivacidade {
    private record Entrada(LadoDesafio lado, Instant expiraEm) {}

    private final Map<String, Entrada> dados = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public Desafio criar(Instant expiraEm) {
        dados.values().removeIf(e -> e.expiraEm().isBefore(Instant.now().minusSeconds(300)));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        LadoDesafio lado = random.nextBoolean() ? LadoDesafio.ESQUERDA : LadoDesafio.DIREITA;
        dados.put(token, new Entrada(lado, expiraEm));
        return new Desafio(token, lado);
    }

    @Override
    public Optional<LadoDesafio> consumir(String token, Instant agora) {
        if (token == null) return Optional.empty();
        Entrada e = dados.remove(token);
        if (e == null || agora.isAfter(e.expiraEm())) return Optional.empty();
        return Optional.of(e.lado());
    }
}
