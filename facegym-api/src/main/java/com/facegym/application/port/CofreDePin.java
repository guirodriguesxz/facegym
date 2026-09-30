package com.facegym.application.port;

/** Hash lento (BCrypt) para PINs: poucos dígitos, então nunca guardar nem comparar em claro. */
public interface CofreDePin {
    String gerarHash(String pin);
    boolean confere(String pin, String hash);
}
