package com.facegym.application;

import com.facegym.application.port.Alunos;
import com.facegym.application.port.CofreDePin;
import com.facegym.application.port.Pins;
import com.facegym.application.port.TentativasDePin;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class PinDoAluno {
    private static final Pattern FORMATO = Pattern.compile("\\d{4,6}");

    private final Pins pins;
    private final CofreDePin cofre;
    private final TentativasDePin tentativas;
    private final Alunos alunos;
    /** Comparado quando não há PIN a conferir: o tempo de resposta não revela quem tem PIN ou cadastro. */
    private final String hashFalso;

    public PinDoAluno(Pins pins, CofreDePin cofre, TentativasDePin tentativas, Alunos alunos) {
        this.pins = pins;
        this.cofre = cofre;
        this.tentativas = tentativas;
        this.alunos = alunos;
        this.hashFalso = cofre.gerarHash(Segredos.gerar());
    }

    public void definir(UUID alunoId, String pin) {
        if (pin == null || !FORMATO.matcher(pin).matches()) throw new IllegalArgumentException("PIN deve ter de 4 a 6 dígitos");
        if (alunos.porId(alunoId).isEmpty()) throw new NaoEncontrado("Aluno");
        pins.definir(alunoId, cofre.gerarHash(pin));
        tentativas.limpar(alunoId);
    }

    /** Todos os caminhos pagam um BCrypt; bloqueado ou sem PIN nunca confere. */
    public boolean confere(UUID alunoId, String pin, Instant agora) {
        String hash = pins.hash(alunoId).orElse(null);
        boolean bloqueado = tentativas.bloqueado(alunoId, agora);
        boolean ok = cofre.confere(pin == null ? "" : pin, hash == null ? hashFalso : hash);
        if (bloqueado || hash == null || !ok) {
            if (!bloqueado) tentativas.falhou(alunoId, agora);
            return false;
        }
        tentativas.limpar(alunoId);
        return true;
    }

    /** Sem aluno para conferir: gasta o mesmo tempo de um PIN errado. */
    public void gastarTempo(String pin) { cofre.confere(pin == null ? "" : pin, hashFalso); }
}
