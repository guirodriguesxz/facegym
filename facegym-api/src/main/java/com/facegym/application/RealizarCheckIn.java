package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.*;
import com.facegym.domain.*;
import com.facegym.domain.vivacidade.LadoDesafio;
import com.facegym.domain.vivacidade.MedidasDeVivacidade;
import com.facegym.domain.vivacidade.PoliticaDeVivacidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class RealizarCheckIn {
    static final Duration VALIDADE_CONFIRMACAO = Duration.ofSeconds(60);
    static final Duration VALIDADE_DESAFIO = Duration.ofSeconds(30);
    static final String PROVA_OBRIGATORIA = "Prova de vida obrigatória";
    static final String DESAFIO_EXPIRADO = "Desafio expirado, tente de novo";
    static final String PROVA_NAO_CONFIRMADA = "Prova de vida não confirmada";

    private final ReconhecimentoFacial reconhecimento;
    private final Alunos alunos;
    private final Planos planos;
    private final Matriculas matriculas;
    private final RegistroDeAcessos acessos;
    private final CheckInsPendentes pendentes;
    private final DesafiosDeVivacidade desafios;
    private final Relogio relogio;
    private final Limiares limiares;
    private final PoliticaDeAcesso politica;
    private final boolean vivacidadeObrigatoria;
    private final PoliticaDeVivacidade politicaDeVivacidade;

    public RealizarCheckIn(ReconhecimentoFacial reconhecimento, Alunos alunos, Planos planos, Matriculas matriculas,
                           RegistroDeAcessos acessos, CheckInsPendentes pendentes, DesafiosDeVivacidade desafios,
                           Relogio relogio, Limiares limiares, Duration antipassback,
                           ConfiguracaoDeVivacidade vivacidade) {
        this.reconhecimento = reconhecimento;
        this.alunos = alunos;
        this.planos = planos;
        this.matriculas = matriculas;
        this.acessos = acessos;
        this.pendentes = pendentes;
        this.desafios = desafios;
        this.relogio = relogio;
        this.limiares = limiares;
        this.politica = new PoliticaDeAcesso(antipassback);
        this.vivacidadeObrigatoria = vivacidade.obrigatoria();
        this.politicaDeVivacidade = new PoliticaDeVivacidade(vivacidade.limiares());
    }

    public DesafiosDeVivacidade.Desafio novoDesafio() {
        return desafios.criar(relogio.agora().plus(VALIDADE_DESAFIO));
    }

    public ResultadoCheckIn porFoto(byte[] foto) {
        if (vivacidadeObrigatoria) {
            registrar(null, ResultadoAcesso.NEGADO, PROVA_OBRIGATORIA, MeioIdentificacao.FACIAL, null, false);
            return new ProvaDeVidaReprovada(PROVA_OBRIGATORIA);
        }
        Identificacao id;
        try {
            id = reconhecimento.identificar(foto);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null, false);
            return new BiometriaIndisponivel();
        }
        return identificado(id, false);
    }

    /** `virada` nula conta como desafio não cumprido; o token é consumido do mesmo jeito. */
    public ResultadoCheckIn porFotoComDesafio(byte[] frente, byte[] virada, String token) {
        Optional<LadoDesafio> lado = desafios.consumir(token, relogio.agora());
        if (lado.isEmpty()) return reprovar(DESAFIO_EXPIRADO, null);
        if (virada == null) return reprovar(PROVA_NAO_CONFIRMADA, null);
        IdentificacaoComVivacidade r;
        try {
            r = reconhecimento.identificarComVivacidade(frente, virada);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null, false);
            return new BiometriaIndisponivel();
        }
        Optional<MedidasDeVivacidade> medidas = r.medidas();
        if (medidas.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, null, false);
            return new NaoReconhecido();
        }
        if (!politicaDeVivacidade.aprova(lado.get(), medidas.get())) return reprovar(PROVA_NAO_CONFIRMADA, r.score());
        return identificado(r.identificacao(), true);
    }

    public ResultadoCheckIn confirmarCpf(String token, String cpf) {
        Cpf informado = Cpf.of(cpf);
        Optional<CheckInsPendentes.Pendente> pendente = pendentes.consumir(token, relogio.agora());
        if (pendente.isEmpty()) return new NaoReconhecido();
        Optional<Aluno> aluno = alunos.porId(pendente.get().alunoId());
        if (aluno.isEmpty() || !aluno.get().cpf().equals(informado)) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não confere com o rosto", MeioIdentificacao.CPF,
                    pendente.get().score(), pendente.get().provaDeVida());
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, pendente.get().score(), pendente.get().provaDeVida());
    }

    public ResultadoCheckIn porCpf(String cpf) {
        Optional<Aluno> aluno = alunos.porCpf(Cpf.of(cpf));
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não cadastrado", MeioIdentificacao.CPF, null, null);
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, null, null);
    }

    /** Câmera de visitantes: só diz de quem é o rosto, sem registrar acesso. */
    public Optional<String> demo(byte[] foto) {
        Identificacao id = reconhecimento.compararDemo(foto);
        if (!id.encontrou() || id.score() < limiares.aceite()) return Optional.empty();
        return alunos.porId(id.alunoId()).map(Aluno::nome);
    }

    private ResultadoCheckIn identificado(Identificacao id, boolean provaDeVida) {
        if (!id.encontrou() || id.score() < limiares.duvida()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, id.score(), provaDeVida);
            return new NaoReconhecido();
        }
        Optional<Aluno> aluno = alunos.porId(id.alunoId());
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Biometria sem aluno cadastrado", MeioIdentificacao.FACIAL, id.score(), provaDeVida);
            return new NaoReconhecido();
        }
        if (id.score() < limiares.aceite()) {
            return new ConfirmarCpf(pendentes.criar(id.alunoId(), id.score(), provaDeVida,
                    relogio.agora().plus(VALIDADE_CONFIRMACAO)));
        }
        return decidir(aluno.get(), MeioIdentificacao.FACIAL, id.score(), provaDeVida);
    }

    /** Sem aluno no registro: a identificação de quem falhou a prova de vida não é confiável. */
    private ResultadoCheckIn reprovar(String motivo, Double score) {
        registrar(null, ResultadoAcesso.NEGADO, motivo, MeioIdentificacao.FACIAL, score, false);
        return new ProvaDeVidaReprovada(motivo);
    }

    private ResultadoCheckIn decidir(Aluno aluno, MeioIdentificacao meio, Double score, Boolean provaDeVida) {
        Instant agora = relogio.agora();
        LocalDateTime local = LocalDateTime.ofInstant(agora, relogio.fuso());
        Optional<Matricula> matricula = matriculas.vigente(aluno.id(), local.toLocalDate());
        Optional<Plano> plano = matricula.flatMap(m -> planos.porId(m.planoId()));
        Instant inicioSemana = Semana.inicio(local.toLocalDate()).atStartOfDay(relogio.fuso()).toInstant();
        long liberados = acessos.liberadosDesde(aluno.id(), inicioSemana);
        Optional<LocalDateTime> ultimaEntrada = acessos.ultimoLiberado(aluno.id())
                .map(i -> LocalDateTime.ofInstant(i, relogio.fuso()));

        Decisao decisao = politica.avaliar(
                new ContextoDeAcesso(aluno, matricula, plano, local, liberados, ultimaEntrada));
        if (decisao instanceof Decisao.Nega nega) {
            registrar(aluno.id(), ResultadoAcesso.NEGADO, nega.motivo(), meio, score, provaDeVida);
            return new Negado(aluno.nome(), nega.motivo());
        }
        registrar(aluno.id(), ResultadoAcesso.LIBERADO, null, meio, score, provaDeVida);
        return new Liberado(aluno.nome(), Boolean.TRUE.equals(provaDeVida));
    }

    private void registrar(UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score,
                           Boolean vivacidade) {
        acessos.registrar(new Acesso(UUID.randomUUID(), relogio.agora(), alunoId, resultado, motivo, meio, score, vivacidade));
    }
}
