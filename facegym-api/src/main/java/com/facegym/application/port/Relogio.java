package com.facegym.application.port;

import java.time.Instant;
import java.time.ZoneId;

public interface Relogio {
    Instant agora();
    ZoneId fuso();
}
