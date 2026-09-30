package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.IdentificacaoComVivacidade;
import com.facegym.domain.*;
import com.facegym.domain.vivacidade.LadoDesafio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealizarCheckInVivacidadeTest {

    static final byte[] FRENTE = {1}, VIRADA = {2};
    static final String CPF_ANA = "529.982.247-25";
    Fakes f;
    Aluno ana;

    @BeforeEach
    void setUp() {
        f = new Fakes();
        Plano livre = new Plano(UUID.randomUUID(), "Livre", new BigDecimal("99.90"),
                EnumSet.allOf(DayOfWeek.class), LocalTime.MIN, LocalTime.MAX, null);
        f.planos.salvar(livre);
        ana = Aluno.novo("Ana", Cpf.of(CPF_ANA), null);
        f.alunos.salvar(ana);
        f.matriculas.salvar(new Matricula(UUID.randomUUID(), ana.id(), livre.id(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
    }

    IdentificacaoComVivacidade medida(double score, double frente, double virada, double similaridade) {
        return new IdentificacaoComVivacidade(ana.id(), score, frente, virada, similaridade);
    }

    Acesso ultimo() { return f.acessos.dados.getLast(); }

    @Test
    void desafioCumpridoLiberaComProvaDeVida() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        f.desafios.proximoLado = LadoDesafio.DIREITA;
        var d = checkIn.novoDesafio();
        assertThat(d.lado()).isEqualTo(LadoDesafio.DIREITA);
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.02, -0.41, 0.8);

        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new Liberado("Ana", true));
        assertThat(ultimo().vivacidade()).isTrue();
        assertThat(ultimo().meio()).isEqualTo(MeioIdentificacao.FACIAL);
    }

    @Test
    void desafioReprovadoNaoRevelaONomeERegistraSemAluno() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio(); // ESQUERDA
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.0, -0.41, 0.8); // virou para a direita

        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Prova de vida não confirmada"));
        assertThat(ultimo().resultado()).isEqualTo(ResultadoAcesso.NEGADO);
        assertThat(ultimo().alunoId()).isNull();
        assertThat(ultimo().vivacidade()).isFalse();
    }

    @Test
    void desafioReutilizadoEExpiradoNemChamamABiometria() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.0, 0.41, 0.8);
        checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token());

        var expirado = new ProvaDeVidaReprovada("Desafio expirado, tente de novo");
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(expirado);

        var outro = checkIn.novoDesafio();
        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(8, 0, 31));
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, outro.token())).isEqualTo(expirado);
        assertThat(f.reconhecimento.chamadasComVivacidade).isEqualTo(1);
    }

    @Test
    void desafioSemFotoViradaConsomeOTokenEReprova() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        assertThat(checkIn.porFotoComDesafio(FRENTE, null, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Prova de vida não confirmada"));
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Desafio expirado, tente de novo"));
        assertThat(f.reconhecimento.chamadasComVivacidade).isZero();
    }

    @Test
    void semRostoNumaDasFotosNaoReconhece() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaNoDesafioCaiNoCpf() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.fora = true;
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new BiometriaIndisponivel());
    }

    @Test
    void obrigatoriaRecusaFotoUnicaMasCpfContinua() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        assertThat(checkIn.porFoto(FRENTE)).isEqualTo(new ProvaDeVidaReprovada("Prova de vida obrigatória"));
        assertThat(ultimo().vivacidade()).isFalse();
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Liberado("Ana"));
        assertThat(ultimo().vivacidade()).isNull();
    }

    @Test
    void opcionalAceitaFotoUnicaSemProvaDeVida() {
        var checkIn = f.checkIn(Duration.ZERO, false);
        f.reconhecimento.proxima = new com.facegym.application.port.Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FRENTE)).isEqualTo(new Liberado("Ana", false));
        assertThat(ultimo().vivacidade()).isFalse();
    }

    @Test
    void scoreDeDuvidaComDesafioCumpridoGuardaAProvaDeVidaNaConfirmacaoPorCpf() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.proximaComVivacidade = medida(0.30, 0.0, 0.41, 0.8);
        var token = ((ConfirmarCpf) checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).token();
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new Liberado("Ana", true));
        assertThat(ultimo().vivacidade()).isTrue();
        assertThat(ultimo().meio()).isEqualTo(MeioIdentificacao.CPF);
    }
}
