package com.facegym.application;

import com.facegym.application.port.*;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;
import com.facegym.domain.Matricula;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/** "Teste com você": aluno temporário com consentimento, apagado em 10 minutos. */
public class VisitantesTemporarios {
    public static final UUID PLANO_VISITANTE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final Duration VALIDADE = Duration.ofMinutes(10);
    public static final int MAX_POR_HORA = 20;

    /** segredo: entregue só a quem cadastrou; exigido para apagar antes do prazo. Guardamos apenas o hash. */
    public record Visitante(UUID id, String nome, String cpf, Instant expiraEm, String segredo) {}

    private final Alunos alunos;
    private final Matriculas matriculas;
    private final Visitantes visitantes;
    private final ReconhecimentoFacial reconhecimento;
    private final Relogio relogio;
    private final SecureRandom random = new SecureRandom();
    private int emAndamento; // cadastros que já passaram pelo limite e ainda não terminaram

    public VisitantesTemporarios(Alunos alunos, Matriculas matriculas, Visitantes visitantes,
                                 ReconhecimentoFacial reconhecimento, Relogio relogio) {
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.visitantes = visitantes;
        this.reconhecimento = reconhecimento;
        this.relogio = relogio;
    }

    public Visitante criar(byte[] foto, boolean consentimento) {
        if (!consentimento) throw new ConsentimentoAusente();
        Instant agora = relogio.agora();
        synchronized (this) {
            if (visitantes.criadosDesde(agora.minus(Duration.ofHours(1))) + emAndamento >= MAX_POR_HORA) throw new LimiteDeVisitantes();
            emAndamento++;
        }
        try {
            return cadastrar(foto, agora);
        } finally {
            synchronized (this) { emAndamento--; }
        }
    }

    private Visitante cadastrar(byte[] foto, Instant agora) {
        Cpf cpf;
        do { cpf = Cpf.aleatorio(random); } while (alunos.porCpf(cpf).isPresent());
        byte[] sufixo = new byte[2];
        random.nextBytes(sufixo);
        Aluno aluno = new Aluno(UUID.randomUUID(), "Visitante " + HexFormat.of().formatHex(sufixo).toUpperCase(),
                cpf, null, false, null, agora);
        alunos.salvar(aluno);

        try {
            reconhecimento.cadastrar(aluno.id(), foto);
        } catch (RostoNaoEncontrado e) {
            alunos.remover(aluno.id()); // sem biometria não existe visitante
            throw e;
        } catch (RuntimeException e) {
            // timeout pode chegar depois de a biometria gravar: apaga o vetor também
            try {
                reconhecimento.remover(aluno.id());
                alunos.remover(aluno.id());
            } catch (RuntimeException remocao) {
                visitantes.registrar(aluno.id(), agora, agora, null); // já expirado: o job limpa os dois lados
            }
            throw e;
        }

        LocalDate hoje = LocalDate.ofInstant(agora, relogio.fuso());
        matriculas.salvar(new Matricula(UUID.randomUUID(), aluno.id(), PLANO_VISITANTE, hoje, hoje.plusDays(1)));
        Instant expiraEm = agora.plus(VALIDADE);
        String segredo = Segredos.gerar();
        visitantes.registrar(aluno.id(), agora, expiraEm, hash(segredo));
        return new Visitante(aluno.id(), aluno.nome(), cpf.valor(), expiraEm, segredo);
    }

    /** Segredo errado e visitante inexistente dão o mesmo erro: não revela quais ids existem. */
    public void remover(UUID id, String segredo) {
        String esperado = visitantes.segredoHash(id).orElse(null);
        if (esperado == null || segredo == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), hash(segredo).getBytes(StandardCharsets.UTF_8))) {
            throw new NaoEncontrado("Visitante");
        }
        reconhecimento.remover(id);
        alunos.remover(id);
    }

    static String hash(String segredo) { return Segredos.sha256(segredo); }

    /** Chamado a cada minuto. Se a biometria estiver fora, tenta de novo na próxima rodada. */
    public int expirar() {
        int removidos = 0;
        for (UUID id : visitantes.expiradosAte(relogio.agora())) {
            try {
                reconhecimento.remover(id);
                alunos.remover(id);
                removidos++;
            } catch (RuntimeException e) {
                // biometria ou banco fora: mantém o registro e a próxima rodada tenta de novo
            }
        }
        return removidos;
    }
}
