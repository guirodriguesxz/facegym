package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Identificacao;
import com.facegym.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealizarCheckInTest {

    static final byte[] FOTO = {1, 2, 3};
    static final byte[] VIRADO = {4, 5, 6};
    static final String CPF_ANA = "529.982.247-25";
    static final String PIN_ANA = "4821";
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
        f.pinDoAluno().definir(ana.id(), PIN_ANA);
        f.matriculas.salvar(new Matricula(UUID.randomUUID(), ana.id(), manha.id(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void scoreAltoLiberaERegistraComoFacial() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new Liberado("Ana"));
        Acesso a = f.acessos.dados.getFirst();
        assertThat(a.resultado()).isEqualTo(ResultadoAcesso.LIBERADO);
        assertThat(a.meio()).isEqualTo(MeioIdentificacao.FACIAL);
        assertThat(a.score()).isEqualTo(0.62);
    }

    @Test
    void scoreNaFaixaDeDuvidaPedeCpfSemRegistrarAcesso() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isInstanceOf(ConfirmarCpf.class);
        assertThat(f.acessos.dados).isEmpty();
    }

    @Test
    void confirmacaoComCpfCertoLibera() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).token();
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new Liberado("Ana"));
        assertThat(f.acessos.dados.getFirst().meio()).isEqualTo(MeioIdentificacao.CPF);
    }

    @Test
    void confirmacaoComCpfDeOutraPessoaNaoLibera() {
        f.alunos.salvar(Aluno.novo("Beto", Cpf.of("11144477735"), null));
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).token();
        assertThat(checkIn.confirmarCpf(token, "111.444.777-35")).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getFirst().motivo()).isEqualTo("CPF não confere com o rosto");
    }

    @Test
    void tokenNaoPodeSerUsadoDuasVezes() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).token();
        checkIn.confirmarCpf(token, CPF_ANA);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void tokenExpiraEm60Segundos() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).token();
        f.relogio.agora = f.relogio.agora.plusSeconds(61);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void scoreBaixoOuNinguemNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.10);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new NaoReconhecido());
        f.reconhecimento.proxima = Identificacao.ninguem();
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaDeAlunoQueNaoExisteMaisNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(UUID.randomUUID(), 0.90);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaDoArCaiNoCpf() {
        f.reconhecimento.fora = true;
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new BiometriaIndisponivel());
        assertThat(checkIn.porFoto(FOTO, null, null, true)).isEqualTo(new BiometriaIndisponivel());
        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, true)).isEqualTo(new Liberado(null));
    }

    @Test
    void cpfPassaPelasMesmasRegrasSemExporNomeNemMotivo() {
        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(13, 0));
        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, true)).isEqualTo(new Negado(null, "Procure a recepção"));
        assertThat(f.acessos.dados).singleElement()
                .satisfies(a -> assertThat(a.motivo()).isEqualTo("Plano Manhã: fora do horário (06:00–12:00)"));
    }

    @Test
    void cpfNaoCadastrado() {
        assertThat(checkIn.porCpf("111.444.777-35", "1234", true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void limiteSemanalContaSoLiberadosDaSemanaAtual() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new Negado("Ana", "Plano Manhã: limite de 2 acessos por semana atingido"));
        f.relogio.set(LocalDate.of(2026, 10, 12).atTime(8, 0)); // segunda seguinte
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isInstanceOf(Liberado.class);
    }

    @Test
    void fotoUnicaDeAlunoRealNaoPassaSemProvaDeVida() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.porFoto(FOTO, null, null, true)).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getFirst().motivo()).isEqualTo("Sem prova de vida");
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30); // nem pede CPF
        assertThat(checkIn.porFoto(FOTO, null, null, true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void provaDeVidaReprovadaNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        f.reconhecimento.reprovarProvaDeVida = true;
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), true)).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getFirst().motivo()).isEqualTo("Prova de vida reprovada");
    }

    @Test
    void alunoDaGaleriaDemoPassaComFotoUnica() {
        f.isentos.add(Cpf.of(CPF_ANA));
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(f.checkIn().porFoto(FOTO, null, null, false)).isEqualTo(new Liberado("Ana"));
    }

    @Test
    void desafioEUsoUnicoEExpira() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        var d = checkIn.novoDesafio();
        assertThat(checkIn.porFoto(FOTO, VIRADO, d.id(), true)).isEqualTo(new Liberado("Ana"));
        assertThat(f.reconhecimento.ultimaDirecao).isEqualTo(d.direcao());
        assertThat(checkIn.porFoto(FOTO, VIRADO, d.id(), true)).isEqualTo(new NaoReconhecido()); // já usado
        assertThat(f.acessos.dados.getLast().motivo()).isEqualTo("Desafio de prova de vida inválido ou expirado");

        var expirado = checkIn.novoDesafio();
        f.relogio.agora = f.relogio.agora.plusSeconds(21);
        assertThat(checkIn.porFoto(FOTO, VIRADO, expirado.id(), true)).isEqualTo(new NaoReconhecido());
        assertThat(checkIn.porFoto(FOTO, VIRADO, null, true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void desafioSorteiaOsDoisLados() {
        var lados = java.util.stream.IntStream.range(0, 64).mapToObj(i -> checkIn.novoDesafio().direcao()).distinct().toList();
        assertThat(lados).hasSize(2);
    }

    @Test
    void semTotemRegistradoAlunoRealNaoPassaNemPorFotoNemPorCpf() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), false)).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getLast().motivo()).isEqualTo("Aluno real fora de totem registrado");
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30); // nem pede CPF
        assertThat(checkIn.porFoto(FOTO, VIRADO, checkIn.novoDesafio().id(), false)).isEqualTo(new NaoReconhecido());

        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, false)).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getLast().motivo()).isEqualTo("CPF fora de totem registrado");
    }

    @Test
    void cpfDeAlunoRealExigePinCerto() {
        assertThat(checkIn.porCpf(CPF_ANA, null, true)).isEqualTo(new NaoReconhecido());
        assertThat(checkIn.porCpf(CPF_ANA, "0000", true)).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getLast().motivo()).isEqualTo("PIN inválido ou bloqueado");
        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, true)).isEqualTo(new Liberado(null));
    }

    @Test
    void cincoPinsErradosBloqueiamMesmoOPinCerto() {
        for (int i = 0; i < 5; i++) checkIn.porCpf(CPF_ANA, "0000", true);
        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, true)).isEqualTo(new NaoReconhecido());
        f.relogio.agora = f.relogio.agora.plus(java.time.Duration.ofMinutes(15));
        assertThat(checkIn.porCpf(CPF_ANA, PIN_ANA, true)).isEqualTo(new Liberado(null));
    }

    @Test
    void alunoSemPinNaoEntraSoPorCpf() {
        var beto = Aluno.novo("Beto", Cpf.of("11144477735"), null);
        f.alunos.salvar(beto);
        assertThat(checkIn.porCpf("111.444.777-35", "1234", true)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void todosOsCaminhosDeRecusaPorCpfGastamUmBcrypt() {
        int antes = f.cofre.comparacoes;
        checkIn.porCpf("390.533.447-05", "1234", true); // não cadastrado
        checkIn.porCpf(CPF_ANA, "1234", false);          // sem totem
        checkIn.porCpf(CPF_ANA, "0000", true);           // PIN errado
        assertThat(f.cofre.comparacoes - antes).isEqualTo(3);
    }

    @Test
    void demoPassaPorCpfSemTotemNemPin() {
        f.isentos.add(Cpf.of(CPF_ANA));
        assertThat(f.checkIn().porCpf(CPF_ANA, null, false)).isEqualTo(new Liberado(null));
    }

    @Test
    void pinComFormatoInvalidoERecusado() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> f.pinDoAluno().definir(ana.id(), "12"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> f.pinDoAluno().definir(UUID.randomUUID(), "1234"))
                .isInstanceOf(NaoEncontrado.class);
    }
}
