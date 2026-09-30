package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.RegraDeAcesso;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Impede que o mesmo acesso libere duas entradas seguidas (passar a vez para outra pessoa). */
public class Antipassback implements RegraDeAcesso {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private final Duration prazo;

    public Antipassback(Duration prazo) { this.prazo = prazo; }

    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        if (prazo.isZero() || c.ultimaEntrada().isEmpty()) return Decisao.libera();
        LocalDateTime liberaEm = c.ultimaEntrada().get().plus(prazo);
        if (c.agora().isBefore(liberaEm)) {
            return Decisao.nega("Entrada já registrada às " + HORA.format(c.ultimaEntrada().get())
                    + "; nova entrada a partir das " + HORA.format(liberaEm));
        }
        return Decisao.libera();
    }
}
