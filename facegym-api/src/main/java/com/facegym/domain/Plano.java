package com.facegym.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record Plano(UUID id, String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim,
                    Integer acessosPorSemana) {
    public Plano {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome do plano é obrigatório");
        if (dias == null || dias.isEmpty()) throw new IllegalArgumentException("Plano precisa de pelo menos um dia");
        if (!inicio.isBefore(fim)) throw new IllegalArgumentException("Horário inicial deve ser antes do final");
        if (acessosPorSemana != null && acessosPorSemana <= 0) throw new IllegalArgumentException("Limite semanal deve ser positivo");
        dias = Set.copyOf(dias);
    }
}
