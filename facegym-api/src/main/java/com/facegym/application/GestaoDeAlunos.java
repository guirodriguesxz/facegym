package com.facegym.application;

import com.facegym.application.port.Alunos;
import com.facegym.application.port.ReconhecimentoFacial;
import com.facegym.application.port.Relogio;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;

import java.util.List;
import java.util.UUID;

public class GestaoDeAlunos {
    private final Alunos alunos;
    private final ReconhecimentoFacial reconhecimento;
    private final Relogio relogio;

    public GestaoDeAlunos(Alunos alunos, ReconhecimentoFacial reconhecimento, Relogio relogio) {
        this.alunos = alunos;
        this.reconhecimento = reconhecimento;
        this.relogio = relogio;
    }

    public Aluno cadastrar(String nome, String cpf, String email) {
        Cpf c = Cpf.of(cpf);
        if (alunos.porCpf(c).isPresent()) throw new IllegalArgumentException("CPF já cadastrado");
        Aluno a = Aluno.novo(nome, c, email);
        alunos.salvar(a);
        return a;
    }

    public List<Aluno> listar() { return alunos.todos(); }

    public void bloquear(UUID id, String motivo) {
        Aluno a = buscar(id);
        a.bloquear(motivo);
        alunos.salvar(a);
    }

    public void desbloquear(UUID id) {
        Aluno a = buscar(id);
        a.desbloquear();
        alunos.salvar(a);
    }

    public void registrarConsentimento(UUID id) {
        Aluno a = buscar(id);
        a.registrarConsentimento(relogio.agora());
        alunos.salvar(a);
    }

    public void cadastrarBiometria(UUID id, byte[] foto) {
        Aluno a = buscar(id);
        if (!a.temConsentimento()) throw new ConsentimentoAusente();
        reconhecimento.cadastrar(a.id(), foto);
    }

    /** Apaga o vetor primeiro: se a biometria estiver fora, o consentimento continua valendo. */
    public void removerBiometria(UUID id) {
        Aluno a = buscar(id);
        reconhecimento.remover(a.id());
        a.revogarConsentimento();
        alunos.salvar(a);
    }

    private Aluno buscar(UUID id) {
        return alunos.porId(id).orElseThrow(() -> new NaoEncontrado("Aluno"));
    }
}
