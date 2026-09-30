package com.facegym.application;

public sealed interface ResultadoCheckIn {
    record Liberado(String nome, boolean provaDeVida) implements ResultadoCheckIn {
        public Liberado(String nome) { this(nome, false); }
    }
    record Negado(String nome, String motivo) implements ResultadoCheckIn {}
    record ConfirmarCpf(String token) implements ResultadoCheckIn {}
    record NaoReconhecido() implements ResultadoCheckIn {}
    record BiometriaIndisponivel() implements ResultadoCheckIn {}
    /** Sem nome: se a prova de vida falhou, a identificação não é confiável. */
    record ProvaDeVidaReprovada(String motivo) implements ResultadoCheckIn {}
}
