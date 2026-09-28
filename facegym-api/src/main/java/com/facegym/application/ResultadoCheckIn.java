package com.facegym.application;

public sealed interface ResultadoCheckIn {
    record Liberado(String nome) implements ResultadoCheckIn {}
    record Negado(String nome, String motivo) implements ResultadoCheckIn {}
    record ConfirmarCpf(String token) implements ResultadoCheckIn {}
    record NaoReconhecido() implements ResultadoCheckIn {}
    record BiometriaIndisponivel() implements ResultadoCheckIn {}
}
