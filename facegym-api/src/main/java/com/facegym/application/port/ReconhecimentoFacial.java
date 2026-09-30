package com.facegym.application.port;

import java.util.UUID;

public interface ReconhecimentoFacial {
    Identificacao identificar(byte[] foto);
    /** Desafio de prova de vida: frente de frente, virado com o rosto girado para o lado sorteado. */
    Identificacao identificarComProvaDeVida(byte[] frente, byte[] virado, DesafiosDeVida.Direcao direcao);
    /** Igual a identificar, mas para a câmera de visitantes: nunca grava nada. */
    Identificacao compararDemo(byte[] foto);
    void cadastrar(UUID alunoId, byte[] foto);
    void remover(UUID alunoId);
}
