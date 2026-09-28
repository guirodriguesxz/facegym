package com.facegym;

import com.facegym.application.port.Relogio;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FluxoCompletoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension bio = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("facegym.biometria.url", bio::baseUrl);
    }

    @TestConfiguration
    static class RelogioFixo {
        @Bean @Primary
        Relogio relogioFixo() {
            ZoneId sp = ZoneId.of("America/Sao_Paulo");
            return new Relogio() {
                public Instant agora() { return LocalDateTime.of(2026, 10, 5, 8, 0).atZone(sp).toInstant(); }
                public ZoneId fuso() { return sp; }
            };
        }
    }

    @Autowired MockMvc mvc;

    String token() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"admin@facegym.dev\",\"senha\":\"admin12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    String id(String json) { return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"); }

    @Test
    void cadastraAlunoComBiometriaEFazCheckInPorRosto() throws Exception {
        String auth = token();
        String plano = id(mvc.perform(post("/api/v1/planos").header("Authorization", auth).contentType("application/json")
                        .content("{\"nome\":\"Manhã\",\"preco\":89.9,\"dias\":[\"MONDAY\",\"TUESDAY\"],\"inicio\":\"06:00\",\"fim\":\"12:00\",\"acessosPorSemana\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String aluno = id(mvc.perform(post("/api/v1/alunos").header("Authorization", auth).contentType("application/json")
                        .content("{\"nome\":\"Ana\",\"cpf\":\"529.982.247-25\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/matriculas").header("Authorization", auth).contentType("application/json")
                        .content("{\"alunoId\":\"" + aluno + "\",\"planoId\":\"" + plano + "\",\"inicio\":\"2026-10-01\",\"vencimento\":\"2026-10-31\"}"))
                .andExpect(status().isCreated());

        var foto = new MockMultipartFile("foto", "f.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // sem consentimento: 409
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/alunos/" + aluno + "/biometria").file(foto).header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/alunos/" + aluno + "/consentimento").header("Authorization", auth))
                .andExpect(status().isNoContent());
        bio.stubFor(WireMock.put("/faces/" + aluno).willReturn(WireMock.noContent()));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/alunos/" + aluno + "/biometria").file(foto).header("Authorization", auth))
                .andExpect(status().isNoContent());

        bio.stubFor(WireMock.post("/faces/identify").willReturn(WireMock.okJson("{\"alunoId\":\"" + aluno + "\",\"score\":0.7}")));
        mvc.perform(multipart("/api/v1/check-ins").file(foto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIBERADO"))
                .andExpect(jsonPath("$.nome").value("Ana"));

        mvc.perform(get("/api/v1/acessos").header("Authorization", auth))
                .andExpect(jsonPath("$[0].resultado").value("LIBERADO"))
                .andExpect(jsonPath("$[0].meio").value("FACIAL"));
    }

    @Test
    void painelExigeLoginMasTotemNao() throws Exception {
        mvc.perform(get("/api/v1/alunos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/check-ins/cpf").contentType("application/json").content("{\"cpf\":\"111.444.777-35\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NAO_RECONHECIDO"));
    }

    @Test
    void cpfInvalidoNoTotemE400() throws Exception {
        mvc.perform(post("/api/v1/check-ins/cpf").contentType("application/json").content("{\"cpf\":\"123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").value("CPF inválido"));
    }

    @Test
    void biometriaForaDoArNoTotemRespondeIndisponivelEHealthContinuaUp() throws Exception {
        bio.stubFor(WireMock.post("/faces/identify").willReturn(WireMock.serverError()));
        mvc.perform(multipart("/api/v1/check-ins").file(new MockMultipartFile("foto", "f.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(jsonPath("$.status").value("BIOMETRIA_INDISPONIVEL"));
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginErrado() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"admin@facegym.dev\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }
}
