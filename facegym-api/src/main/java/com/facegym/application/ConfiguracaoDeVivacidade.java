package com.facegym.application;

import com.facegym.domain.vivacidade.LimiaresDeVivacidade;

/** `obrigatoria = false` só na demo: aceita foto única (alunos fictícios) e registra sem prova de vida. */
public record ConfiguracaoDeVivacidade(boolean obrigatoria, LimiaresDeVivacidade limiares) {
}
