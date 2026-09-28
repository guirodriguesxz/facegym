package com.facegym.application;

public class NaoEncontrado extends RuntimeException {
    public NaoEncontrado(String oque) { super(oque + " não encontrado"); }
}
