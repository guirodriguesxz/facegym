package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.RegraDeAcesso;

public class BloqueioManual implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        return c.aluno().bloqueado() ? Decisao.nega("Aluno bloqueado: " + c.aluno().motivoBloqueio()) : Decisao.libera();
    }
}
