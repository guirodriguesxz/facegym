package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Identificacao;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import com.facegym.domain.Plano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitantesTemporariosTest {

    Fakes f;
    VisitantesTemporarios visitantes;
    final byte[] selfie = {7};

    @BeforeEach
    void setUp() {
        f = new Fakes();
        f.planos.salvar(new Plano(VisitantesTemporarios.PLANO_VISITANTE, "Visitante", BigDecimal.ZERO,
                EnumSet.allOf(DayOfWeek.class), LocalTime.MIN, LocalTime.of(23, 59), null));
        visitantes = new VisitantesTemporarios(f.alunos, f.matriculas, f.visitantes, f.reconhecimento, f.relogio);
    }

    @Test
    void criaVisitanteComConsentimentoBiometriaEMatricula() {
        var v = visitantes.criar(selfie, true);

        assertThat(v.nome()).startsWith("Visitante ");
        assertThat(v.expiraEm()).isEqualTo(f.relogio.agora().plus(VisitantesTemporarios.VALIDADE));
        assertThat(f.alunos.porId(v.id()).orElseThrow().temConsentimento()).isTrue();
        assertThat(f.reconhecimento.cadastrados).containsKey(v.id());

        f.reconhecimento.proxima = new Identificacao(v.id(), 0.9);
        assertThat(f.checkIn().porFoto(selfie)).isEqualTo(new Liberado(v.nome()));
    }

    @Test
    void semConsentimentoNaoCriaNada() {
        assertThatThrownBy(() -> visitantes.criar(selfie, false)).isInstanceOf(ConsentimentoAusente.class);
        assertThat(f.alunos.dados).isEmpty();
        assertThat(f.visitantes.criacoes).isEmpty();
    }

    @Test
    void fotoSemRostoNaoDeixaAlunoFantasmaNemContaNoLimite() {
        var falha = new Fakes.ReconhecimentoFake() {
            @Override public void cadastrar(java.util.UUID id, byte[] foto) { throw new RostoNaoEncontrado("nenhum rosto encontrado"); }
        };
        var v = new VisitantesTemporarios(f.alunos, f.matriculas, f.visitantes, falha, f.relogio);
        assertThatThrownBy(() -> v.criar(selfie, true)).isInstanceOf(RostoNaoEncontrado.class);
        assertThat(f.alunos.dados).isEmpty();
        assertThat(f.visitantes.criacoes).isEmpty();
    }

    @Test
    void limiteDe20PorHora() {
        for (int i = 0; i < VisitantesTemporarios.MAX_POR_HORA; i++) visitantes.criar(selfie, true);
        assertThatThrownBy(() -> visitantes.criar(selfie, true)).isInstanceOf(LimiteDeVisitantes.class);

        f.relogio.agora = f.relogio.agora.plusSeconds(3601);
        visitantes.criar(selfie, true);
    }

    @Test
    void expiraDepoisDe10MinutosEDepoisNaoEReconhecido() {
        var v = visitantes.criar(selfie, true);
        f.relogio.agora = f.relogio.agora.plus(VisitantesTemporarios.VALIDADE).minusSeconds(1);
        assertThat(visitantes.expirar()).isZero();

        f.relogio.agora = f.relogio.agora.plusSeconds(1);
        assertThat(visitantes.expirar()).isEqualTo(1);
        assertThat(f.alunos.porId(v.id())).isEmpty();
        assertThat(f.reconhecimento.cadastrados).doesNotContainKey(v.id());

        // vetor órfão improvável, mas se a biometria ainda devolver o id: não reconhecido
        f.reconhecimento.proxima = new Identificacao(v.id(), 0.9);
        assertThat(f.checkIn().porFoto(selfie)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaNaExpiracaoMantemAlunoParaTentarDeNovo() {
        var v = visitantes.criar(selfie, true);
        f.relogio.agora = f.relogio.agora.plus(VisitantesTemporarios.VALIDADE);
        f.reconhecimento.fora = true;
        assertThat(visitantes.expirar()).isZero();
        assertThat(f.alunos.porId(v.id())).isPresent();

        f.reconhecimento.fora = false;
        assertThat(visitantes.expirar()).isEqualTo(1);
    }

    @Test
    void removerNaHoraSoFuncionaParaVisitante() {
        var v = visitantes.criar(selfie, true);
        visitantes.remover(v.id());
        assertThat(f.alunos.porId(v.id())).isEmpty();

        var aluno = new GestaoDeAlunos(f.alunos, f.reconhecimento, f.relogio).cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> visitantes.remover(aluno.id())).isInstanceOf(NaoEncontrado.class);
        assertThat(f.alunos.porId(aluno.id())).isPresent();
    }
}
