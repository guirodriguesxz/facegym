package com.facegym.adapters.config;

import com.facegym.application.port.Relogio;

import java.time.Instant;
import java.time.ZoneId;

public record RelogioDoSistema(ZoneId fuso) implements Relogio {
    @Override public Instant agora() { return Instant.now(); }
}
