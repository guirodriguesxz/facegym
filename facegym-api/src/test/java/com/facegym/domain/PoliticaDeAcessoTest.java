package com.facegym.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeAcessoTest {

    static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);
    final PoliticaDeAcesso politica = new PoliticaDeAcesso();
    final Plano manha = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 3);
    final Aluno aluno = Aluno.novo("Ana", Cpf.of("52998224725"), "ana@x.com");
    final Matricula matricula = new Matricula(UUID.randomUUID(), aluno.id(), manha.id(),
            SEGUNDA.minusDays(30), SEGUNDA.plusDays(4));

    ContextoDeAcesso ctx(LocalDateTime agora, long liberados) {
        return new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(manha), agora, liberados);
    }

    @Test
    void liberaDentroDoPlano() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(7, 30), 0))).isEqualTo(Decisao.libera());
    }

    @Test
    void horarioInicialEntraEFinalNaoEntra() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(6, 0), 0))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(11, 59), 0))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(12, 0), 0)))
                .isEqualTo(Decisao.nega("Plano Manhã: fora do horário (06:00–12:00)"));
    }

    @Test
    void negaDiaForaDoPlano() {
        LocalDateTime sabado = SEGUNDA.plusDays(5).atTime(8, 0);
        var semVencer = new ContextoDeAcesso(aluno, Optional.of(new Matricula(matricula.id(), aluno.id(), manha.id(),
                SEGUNDA, SEGUNDA.plusDays(30))), Optional.of(manha), sabado, 0);
        assertThat(politica.avaliar(semVencer)).isEqualTo(Decisao.nega("Plano Manhã: não vale no sábado"));
    }

    @Test
    void diaDoVencimentoAindaEntraEDiaSeguinteNao() {
        assertThat(politica.avaliar(ctx(SEGUNDA.plusDays(4).atTime(8, 0), 0))).isEqualTo(Decisao.libera());
        var depois = new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(manha),
                SEGUNDA.plusDays(7).atTime(8, 0), 0);
        assertThat(politica.avaliar(depois)).isEqualTo(Decisao.nega("Plano vencido ou inexistente"));
    }

    @Test
    void semMatriculaNega() {
        var semPlano = new ContextoDeAcesso(aluno, Optional.empty(), Optional.empty(), SEGUNDA.atTime(8, 0), 0);
        assertThat(politica.avaliar(semPlano)).isEqualTo(Decisao.nega("Plano vencido ou inexistente"));
    }

    @Test
    void limiteSemanalContaSoAteOLimite() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(8, 0), 2))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(8, 0), 3)))
                .isEqualTo(Decisao.nega("Plano Manhã: limite de 3 acessos por semana atingido"));
    }

    @Test
    void semLimiteSemanalNuncaNegaPorQuantidade() {
        var livre = new Plano(manha.id(), "Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.MIN, LocalTime.MAX, null);
        var c = new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(livre), SEGUNDA.atTime(8, 0), 999);
        assertThat(politica.avaliar(c)).isEqualTo(Decisao.libera());
    }

    @Test
    void bloqueioVemAntesDeTudo() {
        aluno.bloquear("Falta de atestado");
        var semPlano = new ContextoDeAcesso(aluno, Optional.empty(), Optional.empty(), SEGUNDA.atTime(3, 0), 99);
        assertThat(politica.avaliar(semPlano)).isEqualTo(Decisao.nega("Aluno bloqueado: Falta de atestado"));
    }

    @Test
    void semanaComecaNaSegunda() {
        assertThat(Semana.inicio(SEGUNDA.plusDays(6))).isEqualTo(SEGUNDA);   // domingo
        assertThat(Semana.inicio(SEGUNDA.plusDays(7))).isEqualTo(SEGUNDA.plusDays(7)); // próxima segunda
    }
}
