package com.facegym.adapters.memoria;

import com.facegym.application.port.DesafiosDeVida;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Desafios vivem segundos; memória basta (instância única). */
public class DesafiosDeVidaEmMemoria implements DesafiosDeVida {
    private record Entrada(Direcao direcao, Instant expiraEm) {}

    private final Map<String, Entrada> dados = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String criar(Direcao direcao, Instant expiraEm) {
        // vida de segundos: remover tudo que venceu mantém o mapa do tamanho de ~vazão × validade
        Instant agora = Instant.now();
        dados.values().removeIf(e -> e.expiraEm().isBefore(agora));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        dados.put(id, new Entrada(direcao, expiraEm));
        return id;
    }

    @Override
    public Optional<Direcao> consumir(String id, Instant agora) {
        if (id == null) return Optional.empty();
        Entrada e = dados.remove(id);
        if (e == null || agora.isAfter(e.expiraEm())) return Optional.empty();
        return Optional.of(e.direcao());
    }
}
