package com.facegym.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Segredos aleatórios de 256 bits entregues uma vez; no banco fica só o SHA-256. */
public final class Segredos {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Segredos() {}

    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String sha256(String segredo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
