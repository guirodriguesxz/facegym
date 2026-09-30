package com.facegym.domain;

import com.facegym.domain.regras.Antipassback;
import com.facegym.domain.regras.BloqueioManual;
import com.facegym.domain.regras.HorarioDoPlano;
import com.facegym.domain.regras.LimiteSemanal;
import com.facegym.domain.regras.PlanoAtivo;

import java.time.Duration;
import java.util.List;

public class PoliticaDeAcesso {
    // A ordem importa: a primeira negação é o motivo mostrado no totem.
    private final List<RegraDeAcesso> regras;

    /** Sem antipassback. */
    public PoliticaDeAcesso() { this(Duration.ZERO); }

    public PoliticaDeAcesso(Duration antipassback) {
        regras = List.of(new BloqueioManual(), new Antipassback(antipassback), new PlanoAtivo(),
                new HorarioDoPlano(), new LimiteSemanal());
    }

    public Decisao avaliar(ContextoDeAcesso c) {
        for (RegraDeAcesso regra : regras) {
            Decisao d = regra.avaliar(c);
            if (d instanceof Decisao.Nega) return d;
        }
        return Decisao.libera();
    }
}
