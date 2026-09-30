package com.facegym.adapters.biometria;

import com.facegym.application.port.Identificacao;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiometriaHttpClientTest {

    @RegisterExtension
    static WireMockExtension bio = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final byte[] FOTO = {1, 2, 3};
    static final String CHAVE = "chave-interna-0123456789";
    BiometriaHttpClient client;

    @BeforeEach
    void setUp() {
        var props = new BiometriaProperties(bio.baseUrl(), CHAVE, Duration.ofMillis(300), 4, 50f, Duration.ofSeconds(30));
        client = new BiometriaHttpClient(props, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void identificaEnviandoChaveEFoto() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(post("/faces/identify").withHeader("X-Internal-Key", equalTo(CHAVE))
                .withMultipartRequestBody(aMultipart().withName("image"))
                .willReturn(okJson("{\"alunoId\":\"" + aluno + "\",\"score\":0.73}")));
        assertThat(client.identificar(FOTO)).isEqualTo(new Identificacao(aluno, 0.73));
    }

    @Test
    void ninguemQuandoAlunoIdNulo() {
        bio.stubFor(post("/faces/identify").willReturn(okJson("{\"alunoId\":null,\"score\":null}")));
        assertThat(client.identificar(FOTO).encontrou()).isFalse();
    }

    @Test
    void timeoutFazUmRetryEDepoisFicaIndisponivel() {
        bio.stubFor(post("/faces/identify").willReturn(okJson("{}").withFixedDelay(1000)));
        assertThatThrownBy(() -> client.identificar(FOTO)).isInstanceOf(ReconhecimentoIndisponivel.class);
        bio.verify(2, postRequestedFor(urlEqualTo("/faces/identify")));
    }

    @Test
    void conexaoDerrubadaFazRetry() {
        bio.stubFor(post("/faces/identify").inScenario("queda").whenScenarioStateIs("Started")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("ok"));
        bio.stubFor(post("/faces/identify").inScenario("queda").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"alunoId\":null,\"score\":null}")));
        assertThat(client.identificar(FOTO).encontrou()).isFalse();
    }

    @Test
    void erro500NaoFazRetryMasContaNoCircuito() {
        bio.stubFor(post("/faces/identify").willReturn(serverError()));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.identificar(FOTO)).isInstanceOf(ReconhecimentoIndisponivel.class);
        }
        bio.verify(4, postRequestedFor(urlEqualTo("/faces/identify")));
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // circuito aberto: nem chama a biometria
        assertThatThrownBy(() -> client.identificar(FOTO)).isInstanceOf(ReconhecimentoIndisponivel.class);
        bio.verify(4, postRequestedFor(urlEqualTo("/faces/identify")));
    }

    @Test
    void imagemInvalidaVira422SemAbrirCircuito() {
        bio.stubFor(post("/faces/identify").willReturn(status(422).withBody("{\"detail\":\"imagem inválida\"}")));
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> client.identificar(FOTO)).isInstanceOf(RostoNaoEncontrado.class);
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void cadastroSemRostoLancaRostoNaoEncontrado() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(put("/faces/" + aluno).willReturn(status(422).withBody("{\"detail\":\"nenhum rosto encontrado\"}")));
        assertThatThrownBy(() -> client.cadastrar(aluno, FOTO)).isInstanceOf(RostoNaoEncontrado.class)
                .hasMessage("nenhum rosto encontrado");
    }

    @Test
    void removerChamaDelete() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(delete("/faces/" + aluno).willReturn(noContent()));
        client.remover(aluno);
        bio.verify(deleteRequestedFor(urlEqualTo("/faces/" + aluno)).withHeader("X-Internal-Key", equalTo(CHAVE)));
    }

    @Test
    void provaDeVidaMandaAsDuasFotosELeVivo() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(post("/faces/identify-live").willReturn(okJson("{\"alunoId\":\"" + aluno + "\",\"score\":0.8,\"vivo\":true}")));
        assertThat(client.identificarComProvaDeVida(FOTO, new byte[]{7}, com.facegym.application.port.DesafiosDeVida.Direcao.DIREITA))
                .isEqualTo(new Identificacao(aluno, 0.8, true));
        bio.verify(postRequestedFor(urlEqualTo("/faces/identify-live"))
                .withRequestBodyPart(com.github.tomakehurst.wiremock.client.WireMock.aMultipart().withName("turned").build())
                .withRequestBodyPart(com.github.tomakehurst.wiremock.client.WireMock.aMultipart().withName("direction")
                        .withBody(com.github.tomakehurst.wiremock.client.WireMock.equalTo("DIREITA")).build()));
    }

    @Test
    void demoUsaRotaPropria() {
        bio.stubFor(post("/faces/compare-demo").willReturn(okJson("{\"alunoId\":null,\"score\":null}")));
        client.compararDemo(FOTO);
        bio.verify(postRequestedFor(urlEqualTo("/faces/compare-demo")));
    }

    @Test
    void chaveErradaNaoViraFotoInvalidaEContaNoCircuito() {
        bio.stubFor(post("/faces/identify").willReturn(status(401).withBody("{\"detail\":\"não autorizado\"}")));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.identificar(FOTO)).isInstanceOf(ReconhecimentoIndisponivel.class);
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void removerCom422NaoContaComoIndisponivel() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(delete("/faces/" + aluno).willReturn(status(422).withBody("{\"detail\":\"id inválido\"}")));
        assertThatThrownBy(() -> client.remover(aluno)).isInstanceOf(RostoNaoEncontrado.class);
    }

    @Test
    void aquecerRepetidoFazUmaSoRequisicao() throws InterruptedException {
        bio.stubFor(get("/health").willReturn(okJson("{\"status\":\"UP\"}").withFixedDelay(200)));
        for (int i = 0; i < 20; i++) client.aquecer();
        Thread.sleep(600);
        bio.verify(1, getRequestedFor(urlEqualTo("/health")));
    }

    @Test
    void prontaQuandoHealthResponde() {
        bio.stubFor(get("/health").willReturn(okJson("{\"status\":\"UP\"}")));
        assertThat(client.pronta()).isTrue();
        assertThat(client.pronta()).isTrue();
        bio.verify(1, getRequestedFor(urlEqualTo("/health"))); // resposta positiva fica em cache
    }

    @Test
    void acordandoQuandoHealthFalha() {
        bio.stubFor(get("/health").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(client.pronta()).isFalse();
    }

    @Test
    void urlComBarraNoFinalFunciona() {
        var props = new BiometriaProperties(bio.baseUrl() + "/", CHAVE, Duration.ofMillis(300), 4, 50f, Duration.ofSeconds(30));
        var comBarra = new BiometriaHttpClient(props, CircuitBreakerRegistry.ofDefaults());
        bio.stubFor(get("/health").willReturn(okJson("{\"status\":\"UP\"}")));
        assertThat(comBarra.pronta()).isTrue();
    }
}
