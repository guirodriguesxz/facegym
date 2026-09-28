package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.RegraDeAcesso;

public class PlanoAtivo implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        boolean vigente = c.plano().isPresent()
                && c.matricula().map(m -> m.vigenteEm(c.agora().toLocalDate())).orElse(false);
        return vigente ? Decisao.libera() : Decisao.nega("Plano vencido ou inexistente");
    }
}
