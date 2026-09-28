package com.facegym.application;

public class LimiteDeVisitantes extends RuntimeException {
    public LimiteDeVisitantes() { super("Muitos testes na última hora, tente mais tarde"); }
}
