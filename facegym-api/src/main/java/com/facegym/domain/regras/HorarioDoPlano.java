package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.Plano;
import com.facegym.domain.RegraDeAcesso;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Pressupõe PlanoAtivo avaliada antes (plano presente). */
public class HorarioDoPlano implements RegraDeAcesso {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        Plano p = c.plano().orElseThrow();
        var dia = c.agora().getDayOfWeek();
        if (!p.dias().contains(dia)) {
            String nomeDia = dia.getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
            String artigo = (dia.getValue() >= 6) ? "no" : "na";
            return Decisao.nega("Plano " + p.nome() + ": não vale " + artigo + " " + nomeDia);
        }
        LocalTime hora = c.agora().toLocalTime();
        if (hora.isBefore(p.inicio()) || !hora.isBefore(p.fim())) {
            return Decisao.nega("Plano " + p.nome() + ": fora do horário ("
                    + p.inicio().format(HH_MM) + "–" + p.fim().format(HH_MM) + ")");
        }
        return Decisao.libera();
    }
}
