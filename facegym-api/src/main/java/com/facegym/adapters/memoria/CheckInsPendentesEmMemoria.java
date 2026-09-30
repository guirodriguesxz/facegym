package com.facegym.adapters.memoria;

import com.facegym.application.port.CheckInsPendentes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pendências vivem 60 s; memória basta (instância única). */
public class CheckInsPendentesEmMemoria implements CheckInsPendentes {
    private record Entrada(Pendente pendente, Instant expiraEm) {}

    private final Map<String, Entrada> dados = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String criar(UUID alunoId, Double score, Instant expiraEm) {
        // vida de segundos: remover tudo que venceu mantém o mapa do tamanho de ~vazão × validade
        Instant agora = Instant.now();
        dados.values().removeIf(e -> e.expiraEm().isBefore(agora));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        dados.put(token, new Entrada(new Pendente(alunoId, score), expiraEm));
        return token;
    }

    @Override
    public Optional<Pendente> consumir(String token, Instant agora) {
        if (token == null) return Optional.empty();
        Entrada e = dados.remove(token);
        if (e == null || agora.isAfter(e.expiraEm())) return Optional.empty();
        return Optional.of(e.pendente());
    }
}
