package com.facegym.adapters.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita por IP as rotas públicas: CPF digitado (enumeração de alunos), foto e visitantes (CPU da biometria)
 * e login (força bruta). Janela fixa por regra, em memória (instância única).
 */
@Component
public class LimiteDeTentativas extends OncePerRequestFilter {
    static final long MINUTO_MS = 60_000;
    static final long HORA_MS = 60 * MINUTO_MS;

    /** Acima disto (após limpar as vencidas) novas chaves são recusadas: memória limitada mesmo com IPs rotativos. */
    static final int MAX_CHAVES = 100_000;
    static final long LIMPEZA_MS = 1_000;

    record Regra(String nome, int maximo, long janelaMs) {}
    private record Janela(long inicio, int usadas, long janelaMs) {
        boolean vencida(long agora) { return agora - inicio >= janelaMs; }
    }

    private static final Regra CPF = new Regra("cpf", 10, MINUTO_MS);
    private static final Regra FOTO = new Regra("foto", 30, MINUTO_MS);
    private static final Regra LOGIN = new Regra("login", 10, MINUTO_MS);
    private static final Regra DESAFIO = new Regra("desafio", 30, MINUTO_MS);
    private static final Regra AQUECER = new Regra("aquecer", 60, MINUTO_MS);
    // O teto global (VisitantesTemporarios.MAX_POR_HORA) é 20/h: um IP sozinho não consegue esgotá-lo.
    private static final Regra VISITANTE = new Regra("visitante", 3, HORA_MS);

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong ultimaLimpeza = new java.util.concurrent.atomic.AtomicLong();

    static Regra regra(String metodo, String caminho) {
        if (!"POST".equals(metodo)) return null;
        if (caminho.equals("/api/v1/auth/login")) return LOGIN;
        if (caminho.equals("/api/v1/check-ins")) return FOTO;
        if (caminho.equals("/api/v1/check-ins/desafio")) return DESAFIO;
        if (caminho.startsWith("/api/v1/check-ins/")) return CPF; // /cpf e /{token}/cpf
        if (caminho.equals("/api/v1/demo/visitantes")) return VISITANTE;
        if (caminho.equals("/api/v1/demo/aquecer")) return AQUECER;
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // getServletPath vem decodificado e normalizado, como o Spring usa para achar a rota:
        // com getRequestURI, "/api/v1/%61uth/login" chegava ao login sem passar pelo limite.
        Regra regra = regra(req.getMethod(), req.getServletPath());
        if (regra != null && !permitir(regra.nome() + "|" + req.getRemoteAddr(), regra, System.currentTimeMillis())) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(regra.janelaMs() / 1000));
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"mensagem\":\"Muitas tentativas, aguarde um pouco e tente de novo\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    boolean permitir(String chave, Regra regra, long agora) {
        long ultima = ultimaLimpeza.get();
        if (agora - ultima >= LIMPEZA_MS && ultimaLimpeza.compareAndSet(ultima, agora)) {
            janelas.values().removeIf(j -> j.vencida(agora)); // cada entrada sai quando a própria janela vence
        }
        if (janelas.size() >= MAX_CHAVES && !janelas.containsKey(chave)) return false;
        Janela j = janelas.compute(chave, (k, atual) -> atual == null || atual.vencida(agora)
                ? new Janela(agora, 1, regra.janelaMs()) : new Janela(atual.inicio(), atual.usadas() + 1, atual.janelaMs()));
        return j.usadas() <= regra.maximo();
    }

    int chaves() { return janelas.size(); }
}
