package com.facegym.adapters.jdbc;

import com.facegym.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AlunosJdbc.class, PlanosJdbc.class, MatriculasJdbc.class, AcessosJdbc.class, VisitantesJdbc.class})
class JdbcAdaptersTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AlunosJdbc alunos;
    @Autowired PlanosJdbc planos;
    @Autowired MatriculasJdbc matriculas;
    @Autowired AcessosJdbc acessos;

    @Test
    void alunoIdaEVoltaIncluindoBloqueioEConsentimento() {
        Aluno a = Aluno.novo("Ana", Cpf.of("52998224725"), "ana@x.com");
        alunos.salvar(a);
        a.bloquear("Atestado");
        a.registrarConsentimento(Instant.parse("2026-10-01T10:00:00Z"));
        alunos.salvar(a); // upsert

        Aluno lido = alunos.porCpf(Cpf.of("529.982.247-25")).orElseThrow();
        assertThat(lido.id()).isEqualTo(a.id());
        assertThat(lido.bloqueado()).isTrue();
        assertThat(lido.motivoBloqueio()).isEqualTo("Atestado");
        assertThat(lido.consentimentoBiometricoEm()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(alunos.todos()).hasSize(1);
    }

    @Test
    void planoGuardaDiasEHorarios() {
        Plano p = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 3);
        planos.salvar(p);
        assertThat(planos.porId(p.id())).contains(p);
    }

    @Test
    void matriculaVigenteRespeitaVencimentoInclusivo() {
        Aluno a = Aluno.novo("Beto", Cpf.of("11144477735"), null);
        alunos.salvar(a);
        Plano p = new Plano(UUID.randomUUID(), "Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(5, 0), LocalTime.of(23, 0), null);
        planos.salvar(p);
        Matricula m = new Matricula(UUID.randomUUID(), a.id(), p.id(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        matriculas.salvar(m);

        assertThat(matriculas.vigente(a.id(), LocalDate.of(2026, 10, 10))).contains(m);
        assertThat(matriculas.vigente(a.id(), LocalDate.of(2026, 10, 11))).isEmpty();
    }

    @Test
    void acessosContamSoLiberadosDesdeOInstante() {
        Aluno a = Aluno.novo("Caio", Cpf.of("39053344705"), null);
        alunos.salvar(a);
        Instant t = Instant.parse("2026-10-05T11:00:00Z");
        acessos.registrar(new Acesso(UUID.randomUUID(), t.minusSeconds(60), a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.FACIAL, 0.9));
        acessos.registrar(new Acesso(UUID.randomUUID(), t, a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.CPF, null));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(60), a.id(), ResultadoAcesso.NEGADO, "x", MeioIdentificacao.FACIAL, 0.5));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(90), null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, null));

        assertThat(acessos.liberadosDesde(a.id(), t)).isEqualTo(1);
        assertThat(acessos.ultimoLiberado(a.id())).contains(t);
        assertThat(acessos.ultimoLiberado(UUID.randomUUID())).isEmpty();
        assertThat(acessos.recentes(2)).extracting(Acesso::motivo).containsExactly("Rosto não reconhecido", "x");
    }

    @Autowired VisitantesJdbc visitantes;

    @Test
    void removerAlunoApagaMatriculasAcessosEVisitanteEmCascata() {
        Aluno a = Aluno.novo("Visitante AB12", Cpf.of("16899535009"), null);
        alunos.salvar(a);
        matriculas.salvar(new Matricula(UUID.randomUUID(), a.id(), UUID.fromString("00000000-0000-4000-8000-000000000001"),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)));
        acessos.registrar(new Acesso(UUID.randomUUID(), Instant.now(), a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.FACIAL, 0.9));
        Instant t = Instant.parse("2026-10-05T11:00:00Z");
        visitantes.registrar(a.id(), t, t.plusSeconds(600));

        assertThat(visitantes.existe(a.id())).isTrue();
        assertThat(visitantes.expiradosAte(t.plusSeconds(599))).isEmpty();
        assertThat(visitantes.expiradosAte(t.plusSeconds(600))).containsExactly(a.id());

        alunos.remover(a.id());

        assertThat(alunos.porId(a.id())).isEmpty();
        assertThat(visitantes.existe(a.id())).isFalse();
        assertThat(visitantes.criadosDesde(t.minusSeconds(1))).isEqualTo(1); // log sobrevive
    }
}
