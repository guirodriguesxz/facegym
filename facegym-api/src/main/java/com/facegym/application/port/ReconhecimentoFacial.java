package com.facegym.application.port;

import java.util.UUID;

public interface ReconhecimentoFacial {
    Identificacao identificar(byte[] foto);
    /** Igual a identificar, mas para a câmera de visitantes: nunca grava nada. */
    Identificacao compararDemo(byte[] foto);
    /** Identifica pela foto de frente e mede o giro das duas fotos e a similaridade entre elas. */
    IdentificacaoComVivacidade identificarComVivacidade(byte[] frente, byte[] virada);
    void cadastrar(UUID alunoId, byte[] foto);
    void remover(UUID alunoId);
}
