package com.facegym.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class Semana {
    private Semana() {}

    /** Segunda-feira da semana ISO que contém o dia. */
    public static LocalDate inicio(LocalDate dia) {
        return dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
