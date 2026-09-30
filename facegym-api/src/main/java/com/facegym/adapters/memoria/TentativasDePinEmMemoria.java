package com.facegym.adapters.memoria;

import com.facegym.application.port.TentativasDePin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5 PINs errados bloqueiam o check-in por CPF do aluno por 15 minutos (a facial continua funcionando).
 * Só alunos que existem entram aqui, então o mapa não cresce além do cadastro.
 */
public class TentativasDePinEmMemoria implements TentativasDePin {
    static final int MAX_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private record Estado(int falhas, Instant bloqueadoAte) {}

    private final Map<UUID, Estado> dados = new ConcurrentHashMap<>();

    @Override
    public boolean bloqueado(UUID alunoId, Instant agora) {
        Estado e = dados.get(alunoId);
        return e != null && e.bloqueadoAte() != null && agora.isBefore(e.bloqueadoAte());
    }

    @Override
    public void falhou(UUID alunoId, Instant agora) {
        dados.compute(alunoId, (k, e) -> {
            boolean bloqueioVencido = e != null && e.bloqueadoAte() != null && !agora.isBefore(e.bloqueadoAte());
            int falhas = (e == null || bloqueioVencido ? 0 : e.falhas()) + 1;
            return new Estado(falhas, falhas >= MAX_FALHAS ? agora.plus(BLOQUEIO) : null);
        });
    }

    @Override
    public void limpar(UUID alunoId) { dados.remove(alunoId); }
}
