package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.Plano;
import com.facegym.domain.RegraDeAcesso;

/** Pressupõe PlanoAtivo avaliada antes (plano presente). */
public class LimiteSemanal implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        Plano p = c.plano().orElseThrow();
        if (p.acessosPorSemana() != null && c.liberadosNaSemana() >= p.acessosPorSemana()) {
            return Decisao.nega("Plano " + p.nome() + ": limite de " + p.acessosPorSemana() + " acessos por semana atingido");
        }
        return Decisao.libera();
    }
}
