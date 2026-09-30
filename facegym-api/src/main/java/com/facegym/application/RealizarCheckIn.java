package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.*;
import com.facegym.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

public class RealizarCheckIn {
    static final Duration VALIDADE_CONFIRMACAO = Duration.ofSeconds(60);
    static final String MOTIVO_GENERICO = "Procure a recepção";
    /** Tempo para tirar as duas fotos depois de pedir o desafio. */
    static final Duration VALIDADE_DESAFIO = Duration.ofSeconds(20);

    public record Desafio(String id, DesafiosDeVida.Direcao direcao, Instant expiraEm) {}

    private final ReconhecimentoFacial reconhecimento;
    private final Alunos alunos;
    private final Planos planos;
    private final Matriculas matriculas;
    private final RegistroDeAcessos acessos;
    private final CheckInsPendentes pendentes;
    private final Relogio relogio;
    private final Limiares limiares;
    private final Publico publico;
    private final PinDoAluno pin;
    private final DesafiosDeVida desafios;
    private final SecureRandom random = new SecureRandom();
    private final PoliticaDeAcesso politica = new PoliticaDeAcesso();

    public RealizarCheckIn(ReconhecimentoFacial reconhecimento, Alunos alunos, Planos planos, Matriculas matriculas,
                           RegistroDeAcessos acessos, CheckInsPendentes pendentes, Relogio relogio, Limiares limiares,
                           Publico publico, PinDoAluno pin, DesafiosDeVida desafios) {
        this.reconhecimento = reconhecimento;
        this.alunos = alunos;
        this.planos = planos;
        this.matriculas = matriculas;
        this.acessos = acessos;
        this.pendentes = pendentes;
        this.relogio = relogio;
        this.limiares = limiares;
        this.publico = publico;
        this.pin = pin;
        this.desafios = desafios;
    }

    /** Sorteia para que lado virar o rosto: fotos preparadas antes não sabem o lado. */
    public Desafio novoDesafio() {
        var direcao = DesafiosDeVida.Direcao.values()[random.nextInt(DesafiosDeVida.Direcao.values().length)];
        Instant expiraEm = relogio.agora().plus(VALIDADE_DESAFIO);
        return new Desafio(desafios.criar(direcao, expiraEm), direcao, expiraEm);
    }

    /**
     * virado: segunda foto do desafio de prova de vida (rosto girado para o lado de desafioId). Sem ela
     * só passam os alunos da galeria de demonstração, que só têm foto estática.
     * totem: a requisição veio de um totem registrado. Sem isso, só se alcança o {@link Publico}.
     */
    public ResultadoCheckIn porFoto(byte[] foto, byte[] virado, String desafioId, boolean totem) {
        Optional<DesafiosDeVida.Direcao> direcao = Optional.empty();
        if (virado != null) {
            direcao = desafios.consumir(desafioId, relogio.agora());
            if (direcao.isEmpty()) {
                registrar(null, ResultadoAcesso.NEGADO, "Desafio de prova de vida inválido ou expirado", MeioIdentificacao.FACIAL, null);
                return new NaoReconhecido();
            }
        }
        Identificacao id;
        try {
            id = virado == null ? reconhecimento.identificar(foto)
                    : reconhecimento.identificarComProvaDeVida(foto, virado, direcao.get());
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null);
            return new BiometriaIndisponivel();
        }
        if (virado != null && !id.vivo()) {
            registrar(null, ResultadoAcesso.NEGADO, "Prova de vida reprovada", MeioIdentificacao.FACIAL, null);
            return new NaoReconhecido();
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
        if (!totem && !publico.contem(aluno.get())) {
            registrar(aluno.get().id(), ResultadoAcesso.NEGADO, "Aluno real fora de totem registrado", MeioIdentificacao.FACIAL, id.score());
            return new NaoReconhecido();
        }
        if (!id.vivo() && !publico.demo(aluno.get())) {
            registrar(aluno.get().id(), ResultadoAcesso.NEGADO, "Sem prova de vida", MeioIdentificacao.FACIAL, id.score());
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

    /**
     * Só o CPF não prova quem está na catraca: aluno real precisa de totem registrado e do PIN.
     * Todos os caminhos de recusa respondem igual e gastam o mesmo BCrypt (sem oráculo de cadastro).
     */
    public ResultadoCheckIn porCpf(String cpf, String pinDigitado, boolean totem) {
        Optional<Aluno> aluno = alunos.porCpf(Cpf.of(cpf));
        if (aluno.isEmpty()) {
            pin.gastarTempo(pinDigitado);
            registrar(null, ResultadoAcesso.NEGADO, "CPF não cadastrado", MeioIdentificacao.CPF, null);
            return new NaoReconhecido();
        }
        if (!publico.contem(aluno.get())) {
            if (!totem) {
                pin.gastarTempo(pinDigitado);
                registrar(aluno.get().id(), ResultadoAcesso.NEGADO, "CPF fora de totem registrado", MeioIdentificacao.CPF, null);
                return new NaoReconhecido();
            }
            if (!pin.confere(aluno.get().id(), pinDigitado, relogio.agora())) {
                registrar(aluno.get().id(), ResultadoAcesso.NEGADO, "PIN inválido ou bloqueado", MeioIdentificacao.CPF, null);
                return new NaoReconhecido();
            }
        }
        // O motivo fica no registro de acessos, não na tela.
        return switch (decidir(aluno.get(), MeioIdentificacao.CPF, null)) {
            case Liberado l -> new Liberado(null);
            case Negado n -> new Negado(null, MOTIVO_GENERICO);
            case ResultadoCheckIn outro -> outro;
        };
    }

    private ResultadoCheckIn decidir(Aluno aluno, MeioIdentificacao meio, Double score) {
        Instant agora = relogio.agora();
        LocalDateTime local = LocalDateTime.ofInstant(agora, relogio.fuso());
        Optional<Matricula> matricula = matriculas.vigente(aluno.id(), local.toLocalDate());
        Optional<Plano> plano = matricula.flatMap(m -> planos.porId(m.planoId()));
        Instant inicioSemana = Semana.inicio(local.toLocalDate()).atStartOfDay(relogio.fuso()).toInstant();
        long liberados = acessos.liberadosDesde(aluno.id(), inicioSemana);

        Decisao decisao = politica.avaliar(new ContextoDeAcesso(aluno, matricula, plano, local, liberados));
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
