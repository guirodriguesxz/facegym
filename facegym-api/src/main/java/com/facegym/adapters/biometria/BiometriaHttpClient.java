package com.facegym.adapters.biometria;

import com.facegym.application.port.Identificacao;
import com.facegym.application.port.ReconhecimentoFacial;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

public class BiometriaHttpClient implements ReconhecimentoFacial {

    private static final Logger log = LoggerFactory.getLogger(BiometriaHttpClient.class);

    private record IdentifyResponse(UUID alunoId, Double score) {}

    private static final Duration INTERVALO_AQUECIMENTO = Duration.ofSeconds(60);

    private final String baseUrl;
    private final HttpClient aquecedor = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final java.util.concurrent.atomic.AtomicLong ultimoAquecimento = new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
    private final RestClient http;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public BiometriaHttpClient(BiometriaProperties props, CircuitBreakerRegistry registry) {
        this.baseUrl = props.url();
        var jdk = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1) // uvicorn não fala HTTP/2
                .connectTimeout(props.timeout()).build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.timeout());
        this.http = RestClient.builder().baseUrl(props.url()).requestFactory(factory)
                .defaultHeader("X-Internal-Key", props.chave()).build();

        this.circuitBreaker = registry.circuitBreaker("biometria", CircuitBreakerConfig.custom()
                .slidingWindowSize(props.janela())
                .minimumNumberOfCalls(props.janela())
                .failureRateThreshold(props.taxaFalha())
                .waitDurationInOpenState(props.espera())
                // 4xx é problema da foto, não do serviço: não conta como falha
                .ignoreExceptions(RostoNaoEncontrado.class)
                .build());
        this.retry = Retry.of("biometria", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(ResourceAccessException.class) // timeout e conexão
                .build());
    }

    public CircuitBreaker circuitBreaker() { return circuitBreaker; }

    /**
     * Acorda a biometria (Render free hiberna). Timeout longo, fora do circuito, sem bloquear quem chamou.
     * O endpoint é público: no máximo um aquecimento por minuto, com um cliente só.
     */
    public void aquecer() {
        long agora = System.nanoTime();
        long ultimo = ultimoAquecimento.get();
        if (ultimo != Long.MIN_VALUE && agora - ultimo < INTERVALO_AQUECIMENTO.toNanos()) return;
        if (!ultimoAquecimento.compareAndSet(ultimo, agora)) return;
        Thread.startVirtualThread(() -> {
            try {
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/health"))
                        .timeout(Duration.ofSeconds(90)).GET().build();
                aquecedor.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // melhor esforço
            }
        });
    }

    @Override
    public Identificacao identificar(byte[] foto) {
        return protegido(() -> post("/faces/identify", foto));
    }

    @Override
    public Identificacao compararDemo(byte[] foto) {
        return protegido(() -> post("/faces/compare-demo", foto));
    }

    @Override
    public void cadastrar(UUID alunoId, byte[] foto) {
        protegido(() -> {
            http.put().uri("/faces/{id}", alunoId).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart(foto)).retrieve().onStatus(BiometriaHttpClient::fotoInvalida, BiometriaHttpClient::lancarFotoInvalida)
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void remover(UUID alunoId) {
        protegido(() -> {
            http.delete().uri("/faces/{id}", alunoId).retrieve()
                    .onStatus(BiometriaHttpClient::fotoInvalida, BiometriaHttpClient::lancarFotoInvalida)
                    .toBodilessEntity();
            return null;
        });
    }

    private Identificacao post(String rota, byte[] foto) {
        IdentifyResponse r = http.post().uri(rota).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(foto)).retrieve().onStatus(BiometriaHttpClient::fotoInvalida, BiometriaHttpClient::lancarFotoInvalida)
                .body(IdentifyResponse.class);
        return r == null ? Identificacao.ninguem() : new Identificacao(r.alunoId(), r.score());
    }

    private <T> T protegido(Supplier<T> chamada) {
        Supplier<T> decorada = CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, chamada));
        try {
            return decorada.get();
        } catch (RostoNaoEncontrado e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Biometria indisponível em {}: {}", baseUrl, e.toString());
            throw new ReconhecimentoIndisponivel("Serviço de biometria indisponível", e);
        }
    }

    /**
     * Só 413/422 são problema da requisição (foto ou id). Outros 4xx, como 401 por
     * chave interna errada, são falha de integração e precisam abrir o circuito.
     */
    private static boolean fotoInvalida(HttpStatusCode status) {
        return status.value() == 413 || status.value() == 422;
    }

    private static void lancarFotoInvalida(org.springframework.http.HttpRequest req,
                                           org.springframework.http.client.ClientHttpResponse res) throws java.io.IOException {
        throw new RostoNaoEncontrado(detalhe(new String(res.getBody().readAllBytes())));
    }

    private static LinkedMultiValueMap<String, Object> multipart(byte[] foto) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("image", new ByteArrayResource(foto) {
            @Override public String getFilename() { return "foto.jpg"; }
        });
        return parts;
    }

    private static String detalhe(String corpo) {
        var m = java.util.regex.Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]*)\"").matcher(corpo);
        return m.find() ? m.group(1) : "imagem inválida";
    }
}
