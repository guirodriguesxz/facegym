package com.facegym.adapters.memoria;

/** O endpoint de desafios é público: um teto evita encher a memória com pedidos em massa. */
public class MuitosDesafios extends RuntimeException {
    public MuitosDesafios() { super("Muitos check-ins ao mesmo tempo, tente em instantes"); }
}
