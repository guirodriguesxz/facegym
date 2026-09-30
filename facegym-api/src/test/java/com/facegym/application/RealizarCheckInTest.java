package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Identificacao;
import com.facegym.domain.*;
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

class RealizarCheckInTest {

    static final byte[] FOTO = {1, 2, 3};
    static final String CPF_ANA = "529.982.247-25";
    Fakes f;
    RealizarCheckIn checkIn;
    Aluno ana;

    @BeforeEach
    void setUp() {
        f = new Fakes();
        checkIn = f.checkIn();
        Plano manha = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 2);
        f.planos.salvar(manha);
        ana = Aluno.novo("Ana", Cpf.of(CPF_ANA), null);
        f.alunos.salvar(ana);
        f.matriculas.salvar(new Matricula(UUID.randomUUID(), ana.id(), manha.id(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void scoreAltoLiberaERegistraComoFacial() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Liberado("Ana"));
        Acesso a = f.acessos.dados.getFirst();
        assertThat(a.resultado()).isEqualTo(ResultadoAcesso.LIBERADO);
        assertThat(a.meio()).isEqualTo(MeioIdentificacao.FACIAL);
        assertThat(a.score()).isEqualTo(0.62);
    }

    @Test
    void scoreNaFaixaDeDuvidaPedeCpfSemRegistrarAcesso() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(ConfirmarCpf.class);
        assertThat(f.acessos.dados).isEmpty();
    }

    @Test
    void confirmacaoComCpfCertoLibera() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new Liberado("Ana"));
        assertThat(f.acessos.dados.getFirst().meio()).isEqualTo(MeioIdentificacao.CPF);
    }

    @Test
    void confirmacaoComCpfDeOutraPessoaNaoLibera() {
        f.alunos.salvar(Aluno.novo("Beto", Cpf.of("11144477735"), null));
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        assertThat(checkIn.confirmarCpf(token, "111.444.777-35")).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getFirst().motivo()).isEqualTo("CPF não confere com o rosto");
    }

    @Test
    void tokenNaoPodeSerUsadoDuasVezes() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        checkIn.confirmarCpf(token, CPF_ANA);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void tokenExpiraEm60Segundos() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        f.relogio.agora = f.relogio.agora.plusSeconds(61);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void scoreBaixoOuNinguemNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.10);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
        f.reconhecimento.proxima = Identificacao.ninguem();
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaDeAlunoQueNaoExisteMaisNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(UUID.randomUUID(), 0.90);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaDoArCaiNoCpf() {
        f.reconhecimento.fora = true;
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new BiometriaIndisponivel());
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Liberado("Ana"));
    }

    @Test
    void cpfPassaPelasMesmasRegras() {
        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(13, 0));
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Negado("Ana", "Plano Manhã: fora do horário (06:00–12:00)"));
    }

    @Test
    void cpfNaoCadastrado() {
        assertThat(checkIn.porCpf("111.444.777-35")).isEqualTo(new NaoReconhecido());
    }

    @Test
    void limiteSemanalContaSoLiberadosDaSemanaAtual() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Negado("Ana", "Plano Manhã: limite de 2 acessos por semana atingido"));
        f.relogio.set(LocalDate.of(2026, 10, 12).atTime(8, 0)); // segunda seguinte
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
    }

    @Test
    void demoDevolveNomeSemRegistrarNada() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.demo(FOTO)).contains("Ana");
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.2);
        assertThat(checkIn.demo(FOTO)).isEmpty();
        assertThat(f.acessos.dados).isEmpty();
    }

    @Test
    void antipassbackNegaSegundaEntradaDentroDoPrazoELiberaDepois() {
        checkIn = f.checkIn(Duration.ofMinutes(5));
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Liberado("Ana"));

        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(8, 3));
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(
                new Negado("Ana", "Entrada já registrada às 08:00; nova entrada a partir das 08:05"));

        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(8, 5));
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Liberado("Ana"));
    }
}
