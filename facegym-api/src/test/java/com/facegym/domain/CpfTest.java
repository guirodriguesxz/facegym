package com.facegym.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @Test
    void aceitaComOuSemMascara() {
        assertThat(Cpf.of("529.982.247-25")).isEqualTo(Cpf.of("52998224725"));
        assertThat(Cpf.of(" 529.982.247-25 ").valor()).isEqualTo("52998224725");
    }

    @Test
    void rejeitaDigitoVerificadorErrado() {
        assertThatThrownBy(() -> Cpf.of("529.982.247-26")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaSequenciasRepetidasETamanhoErrado() {
        assertThatThrownBy(() -> Cpf.of("111.111.111-11")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cpf.of("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cpf.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
