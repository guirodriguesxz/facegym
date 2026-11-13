package com.facegym.adapters.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limite de requisições por IP e por grupo de rotas, em janela fixa e em memória (uma instância só).
 * O IP vem de {@code getRemoteAddr}: com {@code server.forward-headers-strategy=native} o Tomcat
 * já troca pelo cliente real atrás do proxy, sem confiar num cabeçalho que o cliente possa forjar.
 */
public class LimiteDeRequisicoes extends OncePerRequestFilter {
    static final Duration JANELA = Duration.ofMinutes(1);
    private static final int MAX_CHAVES = 10_000;

    private record Janela(long inicio, int usadas) {}

    private final int login;
    private final int cpf;
    private final int geral;
    private final LongSupplier relogioMs;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public LimiteDeRequisicoes(int login, int cpf, int geral) {
        this(login, cpf, geral, System::currentTimeMillis);
    }

    LimiteDeRequisicoes(int login, int cpf, int geral, LongSupplier relogioMs) {
        this.login = login;
        this.cpf = cpf;
        this.geral = geral;
        this.relogioMs = relogioMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        String grupo = null;
        int limite = 0;
        if (!"POST".equals(req.getMethod()) && !"DELETE".equals(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }
        if (path.equals("/api/v1/auth/login")) { grupo = "login"; limite = login; }
        else if (path.equals("/api/v1/check-ins/cpf") || path.matches("/api/v1/check-ins/[^/]+/cpf")) { grupo = "cpf"; limite = cpf; }
        else if (path.startsWith("/api/v1/check-ins") || path.startsWith("/api/v1/demo")) { grupo = "geral"; limite = geral; }

        if (grupo != null && !permitir(grupo + "|" + req.getRemoteAddr(), limite)) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(JANELA.toSeconds()));
            res.setContentType("application/json");
            res.getWriter().write("{\"erro\":\"Muitas tentativas, aguarde um minuto\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    boolean permitir(String chave, int limite) {
        long agora = relogioMs.getAsLong();
        if (janelas.size() > MAX_CHAVES) janelas.values().removeIf(j -> agora - j.inicio() >= JANELA.toMillis());
        boolean[] ok = {false};
        janelas.compute(chave, (k, atual) -> {
            if (atual == null || agora - atual.inicio() >= JANELA.toMillis()) {
                ok[0] = true;
                return new Janela(agora, 1);
            }
            if (atual.usadas() >= limite) return atual;
            ok[0] = true;
            return new Janela(atual.inicio(), atual.usadas() + 1);
        });
        return ok[0];
    }
}
