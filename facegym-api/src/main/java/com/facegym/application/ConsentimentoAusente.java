package com.facegym.application;

public class ConsentimentoAusente extends RuntimeException {
    public ConsentimentoAusente() { super("Aluno não deu consentimento para uso de biometria"); }
}
