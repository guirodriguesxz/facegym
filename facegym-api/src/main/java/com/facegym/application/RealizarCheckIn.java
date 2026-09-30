package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.*;
import com.facegym.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class RealizarCheckIn {
    static final Duration VALIDADE_CONFIRMACAO = Duration.ofSeconds(60);

    private final ReconhecimentoFacial reconhecimento;
    private final Alunos alunos;
    private final Planos planos;
    private final Matriculas matriculas;
    private final RegistroDeAcessos acessos;
    private final CheckInsPendentes pendentes;
    private final Relogio relogio;
    private final Limiares limiares;
    private final PoliticaDeAcesso politica;

    public RealizarCheckIn(ReconhecimentoFacial reconhecimento, Alunos alunos, Planos planos, Matriculas matriculas,
                           RegistroDeAcessos acessos, CheckInsPendentes pendentes, Relogio relogio, Limiares limiares,
                           Duration antipassback) {
        this.reconhecimento = reconhecimento;
        this.alunos = alunos;
        this.planos = planos;
        this.matriculas = matriculas;
        this.acessos = acessos;
        this.pendentes = pendentes;
        this.relogio = relogio;
        this.limiares = limiares;
        this.politica = new PoliticaDeAcesso(antipassback);
    }

    public ResultadoCheckIn porFoto(byte[] foto) {
        Identificacao id;
        try {
            id = reconhecimento.identificar(foto);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null);
            return new BiometriaIndisponivel();
        }
        if (!id.encontrou() || id.score() < limiares.duvida()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, id.score());
            return new NaoReconhecido();
        }
        Optional<Aluno> aluno = alunos.porId(id.alunoId());
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Biometria sem aluno cadastrado", MeioIdentificacao.FACIAL, id.score());
            return new NaoReconhecido();
        }
        if (id.score() < limiares.aceite()) {
            return new ConfirmarCpf(pendentes.criar(id.alunoId(), id.score(), relogio.agora().plus(VALIDADE_CONFIRMACAO)));
        }
        return decidir(aluno.get(), MeioIdentificacao.FACIAL, id.score());
    }

    public ResultadoCheckIn confirmarCpf(String token, String cpf) {
        Cpf informado = Cpf.of(cpf);
        Optional<CheckInsPendentes.Pendente> pendente = pendentes.consumir(token, relogio.agora());
        if (pendente.isEmpty()) return new NaoReconhecido();
        Optional<Aluno> aluno = alunos.porId(pendente.get().alunoId());
        if (aluno.isEmpty() || !aluno.get().cpf().equals(informado)) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não confere com o rosto", MeioIdentificacao.CPF, pendente.get().score());
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, pendente.get().score());
    }

    public ResultadoCheckIn porCpf(String cpf) {
        Optional<Aluno> aluno = alunos.porCpf(Cpf.of(cpf));
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não cadastrado", MeioIdentificacao.CPF, null);
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, null);
    }

    /** Câmera de visitantes: só diz de quem é o rosto, sem registrar acesso. */
    public Optional<String> demo(byte[] foto) {
        Identificacao id = reconhecimento.compararDemo(foto);
        if (!id.encontrou() || id.score() < limiares.aceite()) return Optional.empty();
        return alunos.porId(id.alunoId()).map(Aluno::nome);
    }

    private ResultadoCheckIn decidir(Aluno aluno, MeioIdentificacao meio, Double score) {
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
            registrar(aluno.id(), ResultadoAcesso.NEGADO, nega.motivo(), meio, score);
            return new Negado(aluno.nome(), nega.motivo());
        }
        registrar(aluno.id(), ResultadoAcesso.LIBERADO, null, meio, score);
        return new Liberado(aluno.nome());
    }

    private void registrar(UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score) {
        acessos.registrar(new Acesso(UUID.randomUUID(), relogio.agora(), alunoId, resultado, motivo, meio, score));
    }
}
