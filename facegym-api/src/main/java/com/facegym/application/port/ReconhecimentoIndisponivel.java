package com.facegym.application.port;

public class ReconhecimentoIndisponivel extends RuntimeException {
    public ReconhecimentoIndisponivel(String msg, Throwable causa) { super(msg, causa); }
}
