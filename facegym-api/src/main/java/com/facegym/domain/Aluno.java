package com.facegym.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Aluno {
    private final UUID id;
    private final String nome;
    private final Cpf cpf;
    private final String email;
    private boolean bloqueado;
    private String motivoBloqueio;
    private Instant consentimentoBiometricoEm;

    public Aluno(UUID id, String nome, Cpf cpf, String email, boolean bloqueado, String motivoBloqueio,
                 Instant consentimentoBiometricoEm) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        this.id = Objects.requireNonNull(id);
        this.nome = nome.strip();
        this.cpf = Objects.requireNonNull(cpf);
        this.email = email;
        this.bloqueado = bloqueado;
        this.motivoBloqueio = motivoBloqueio;
        this.consentimentoBiometricoEm = consentimentoBiometricoEm;
    }

    public static Aluno novo(String nome, Cpf cpf, String email) {
        return new Aluno(UUID.randomUUID(), nome, cpf, email, false, null, null);
    }

    public void bloquear(String motivo) {
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Motivo do bloqueio é obrigatório");
        this.bloqueado = true;
        this.motivoBloqueio = motivo.strip();
    }

    public void desbloquear() {
        this.bloqueado = false;
        this.motivoBloqueio = null;
    }

    public void registrarConsentimento(Instant quando) { this.consentimentoBiometricoEm = Objects.requireNonNull(quando); }
    public void revogarConsentimento() { this.consentimentoBiometricoEm = null; }
    public boolean temConsentimento() { return consentimentoBiometricoEm != null; }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public Cpf cpf() { return cpf; }
    public String email() { return email; }
    public boolean bloqueado() { return bloqueado; }
    public String motivoBloqueio() { return motivoBloqueio; }
    public Instant consentimentoBiometricoEm() { return consentimentoBiometricoEm; }
}
