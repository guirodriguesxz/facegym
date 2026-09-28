package com.facegym.application;

import com.facegym.application.port.ReconhecimentoIndisponivel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestaoDeAlunosTest {

    final Fakes f = new Fakes();
    final GestaoDeAlunos gestao = new GestaoDeAlunos(f.alunos, f.reconhecimento, f.relogio);
    final GestaoDePlanos planos = new GestaoDePlanos(f.planos, f.alunos, f.matriculas);
    final byte[] foto = {9};

    @Test
    void biometriaExigeConsentimento() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> gestao.cadastrarBiometria(ana.id(), foto)).isInstanceOf(ConsentimentoAusente.class);
        assertThat(f.reconhecimento.cadastrados).isEmpty();

        gestao.registrarConsentimento(ana.id());
        gestao.cadastrarBiometria(ana.id(), foto);
        assertThat(f.reconhecimento.cadastrados).containsKey(ana.id());
    }

    @Test
    void removerBiometriaApagaVetorERevogaConsentimento() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.registrarConsentimento(ana.id());
        gestao.cadastrarBiometria(ana.id(), foto);

        gestao.removerBiometria(ana.id());

        assertThat(f.reconhecimento.cadastrados).isEmpty();
        assertThat(f.alunos.porId(ana.id()).orElseThrow().temConsentimento()).isFalse();
    }

    @Test
    void seBiometriaEstaForaDoArConsentimentoNaoERevogado() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.registrarConsentimento(ana.id());
        f.reconhecimento.fora = true;
        assertThatThrownBy(() -> gestao.removerBiometria(ana.id())).isInstanceOf(ReconhecimentoIndisponivel.class);
        assertThat(f.alunos.porId(ana.id()).orElseThrow().temConsentimento()).isTrue();
    }

    @Test
    void cpfDuplicadoERejeitado() {
        gestao.cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> gestao.cadastrar("Outra", "52998224725", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CPF já cadastrado");
    }

    @Test
    void bloquearEDesbloquear() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.bloquear(ana.id(), "Falta de atestado");
        assertThat(f.alunos.porId(ana.id()).orElseThrow().bloqueado()).isTrue();
        gestao.desbloquear(ana.id());
        assertThat(f.alunos.porId(ana.id()).orElseThrow().bloqueado()).isFalse();
    }

    @Test
    void alunoInexistente() {
        assertThatThrownBy(() -> gestao.bloquear(UUID.randomUUID(), "x")).isInstanceOf(NaoEncontrado.class);
    }

    @Test
    void matriculaExigeAlunoEPlanoExistentes() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        var livre = planos.cadastrar("Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(5, 0), LocalTime.of(23, 0), null);
        var m = planos.matricular(ana.id(), livre.id(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(f.matriculas.vigente(ana.id(), LocalDate.of(2026, 10, 15))).contains(m);
        assertThatThrownBy(() -> planos.matricular(ana.id(), UUID.randomUUID(), LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NaoEncontrado.class);
    }
}
