# FaceGym API — Implementation Plan (2 de 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** API `facegym-api` em Spring Boot com arquitetura hexagonal: check-in por rosto ou CPF avaliando 4 regras de acesso, gestão de alunos/planos/matrículas/biometria pelo painel e integração resiliente com o serviço de biometria.

**Architecture:** `domain` (Java puro: regras e política) ← `application` (casos de uso + portas) ← `adapters` (REST, JDBC, cliente HTTP da biometria, segurança). ArchUnit garante que `domain` e `application` não importam Spring, JDBC nem HTTP. A biometria é só um adaptador da porta `ReconhecimentoFacial`, com timeout, retry e circuit breaker (Resilience4j); se cair, o totem usa CPF.

**Tech Stack:** Java 21, Spring Boot 3.3.2 (web, validation, jdbc, security, oauth2-resource-server, actuator), Flyway, PostgreSQL 16, Resilience4j 2.2.0, JUnit 5, AssertJ, ArchUnit 1.3.0, WireMock 3.9.1, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-28-facegym-design.md` (seções 2.1, 3, 4, 5, 7). Contrato da biometria e limiares: plano 1 (`docs/superpowers/plans/2026-09-28-facegym-biometria.md`, Task 5) e `facegym-biometria/CALIBRATION.md`.

## Global Constraints

- Pacote raiz `com.facegym`; subpacotes `domain`, `application`, `adapters`.
- `domain` e `application` sem imports de `org.springframework..`, `jakarta..`, `java.sql..`, `java.net.http..`.
- Fuso da academia: `America/Sao_Paulo` (configurável `facegym.fuso`).
- Limiares padrão: **aceite 0.41, dúvida 0.18** (`CALIBRATION.md`; substituem 0.80/0.60 da spec).
- Token de confirmação por CPF válido por **60 s**, uso único.
- Ordem das regras: `BloqueioManual`, `PlanoAtivo`, `HorarioDoPlano`, `LimiteSemanal`; primeira negação define o motivo.
- Vencimento inclusivo; horário `[inicio, fim)`; semana ISO segunda 00:00 → domingo 23:59 no fuso da academia; só acessos LIBERADOS contam.
- Biometria: timeout 2 s; 1 retry só em erro de conexão/timeout; circuit breaker 50% de falha em janela de 10, meia-abertura após 30 s.
- Biometria exige consentimento registrado; remover biometria revoga o consentimento.
- Foto até 5 MB. Nunca logar foto.
- Estado do circuito não pode derrubar o `/actuator/health` (o Render reiniciaria o serviço).

## Review Focus

1. **CPF digitado com máscara** (`123.456.789-09`) no totem — deve ser aceito igual a só dígitos. Teste na Task 2 (`Cpf`).
2. **Check-in exatamente no limite do horário** (06:00 entra, 12:00 não entra num plano 6–12) — teste na Task 2.
3. **Token de confirmação reutilizado ou expirado** — deve responder "não reconhecido", nunca liberar duas vezes. Teste na Task 3.
4. **Biometria devolve id de aluno que não existe mais** (vetor órfão) — deve responder "não reconhecido", não 500. Teste na Task 3.
5. **Biometria responde 4xx (imagem inválida)** — deve virar 422 para o totem e **não** contar como falha no circuit breaker. Teste na Task 6.

## Decisões de implementação

- **JDBC (`JdbcClient`) em vez de JPA** nos adaptadores de persistência: o domínio fica sem anotações e o mapeamento é explícito. A spec cita JPA como detalhe do adaptador; a fronteira hexagonal é a mesma.
- **Casos de uso agrupados** em `RealizarCheckIn`, `GestaoDeAlunos` e `GestaoDePlanos` (a spec lista um caso por operação; os métodos são os mesmos, com menos arquivos).

## Estrutura de arquivos

```
facegym-api/
  pom.xml, mvnw, .mvn/
  Dockerfile
  src/main/java/com/facegym/
    FaceGymApplication.java
    domain/
      Cpf.java, Aluno.java, Plano.java, Matricula.java, Acesso.java,
      ResultadoAcesso.java, MeioIdentificacao.java, Decisao.java,
      ContextoDeAcesso.java, RegraDeAcesso.java, Semana.java, PoliticaDeAcesso.java
      regras/BloqueioManual.java, PlanoAtivo.java, HorarioDoPlano.java, LimiteSemanal.java
    application/
      port/ReconhecimentoFacial.java, Identificacao.java, ReconhecimentoIndisponivel.java,
           RostoNaoEncontrado.java, Alunos.java, Planos.java, Matriculas.java,
           RegistroDeAcessos.java, Relogio.java, CheckInsPendentes.java
      Limiares.java, ResultadoCheckIn.java, RealizarCheckIn.java,
      NaoEncontrado.java, ConsentimentoAusente.java, GestaoDeAlunos.java, GestaoDePlanos.java
    adapters/
      memoria/CheckInsPendentesEmMemoria.java
      jdbc/AlunosJdbc.java, PlanosJdbc.java, MatriculasJdbc.java, AcessosJdbc.java
      biometria/BiometriaHttpClient.java, BiometriaProperties.java
      web/CheckInController.java, AdminController.java, AuthController.java, ErrosHandler.java
      config/UseCaseConfig.java, SecurityConfig.java, AdminBootstrap.java, RelogioDoSistema.java
  src/main/resources/application.yml, db/migration/V1__schema.sql
  src/test/java/com/facegym/
    ArquiteturaTest.java
    domain/CpfTest.java, PoliticaDeAcessoTest.java
    application/Fakes.java, RealizarCheckInTest.java, GestaoDeAlunosTest.java
    adapters/jdbc/JdbcAdaptersTest.java
    adapters/biometria/BiometriaHttpClientTest.java
    FluxoCompletoIT.java
.github/workflows/api.yml
docker-compose.yml (raiz)
```

---

### Task 1: Projeto Spring e regra de arquitetura

**Files:**
- Create: `facegym-api/pom.xml`, `facegym-api/mvnw`, `facegym-api/.mvn/wrapper/maven-wrapper.properties` (copiados de `~/Downloads/physiomanage`)
- Create: `facegym-api/src/main/java/com/facegym/FaceGymApplication.java`
- Create: `facegym-api/src/main/java/com/facegym/domain/package-info.java`, `.../application/package-info.java`
- Test: `facegym-api/src/test/java/com/facegym/ArquiteturaTest.java`

**Interfaces:**
- Produces: projeto Maven compilável; regra ArchUnit que as tasks seguintes precisam respeitar.

- [ ] **Step 1: pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>
    <groupId>com.facegym</groupId>
    <artifactId>facegym-api</artifactId>
    <version>0.1.0</version>
    <properties>
        <java.version>21</java.version>
        <resilience4j.version>2.2.0</resilience4j.version>
    </properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-circuitbreaker</artifactId><version>${resilience4j.version}</version></dependency>
        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-retry</artifactId><version>${resilience4j.version}</version></dependency>
        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-micrometer</artifactId><version>${resilience4j.version}</version></dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
        <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId><version>1.3.0</version><scope>test</scope></dependency>
        <dependency><groupId>org.wiremock</groupId><artifactId>wiremock-standalone</artifactId><version>3.9.1</version><scope>test</scope></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions><execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution></executions>
            </plugin>
        </plugins>
    </build>
</project>
```

```bash
cp ~/Downloads/physiomanage/mvnw facegym-api/ && mkdir -p facegym-api/.mvn/wrapper && cp ~/Downloads/physiomanage/.mvn/wrapper/maven-wrapper.properties facegym-api/.mvn/wrapper/
```

- [ ] **Step 2: Aplicação e marcadores de pacote**

```java
// facegym-api/src/main/java/com/facegym/FaceGymApplication.java
package com.facegym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FaceGymApplication {
    public static void main(String[] args) {
        SpringApplication.run(FaceGymApplication.class, args);
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/package-info.java
/** Regras de negócio da academia. Java puro: nada de Spring, JDBC ou HTTP. */
package com.facegym.domain;
```

```java
// facegym-api/src/main/java/com/facegym/application/package-info.java
/** Casos de uso e portas. Depende só do domínio. */
package com.facegym.application;
```

- [ ] **Step 3: Teste de arquitetura**

```java
// facegym-api/src/test/java/com/facegym/ArquiteturaTest.java
package com.facegym;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.facegym", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule nucleoSemFrameworks = noClasses()
            .that().resideInAnyPackage("com.facegym.domain..", "com.facegym.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "java.sql..", "java.net.http..", "io.github.resilience4j..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dominioNaoConheceAplicacao = noClasses()
            .that().resideInAPackage("com.facegym.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("com.facegym.application..", "com.facegym.adapters..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule aplicacaoNaoConheceAdaptadores = noClasses()
            .that().resideInAPackage("com.facegym.application..")
            .should().dependOnClassesThat().resideInAPackage("com.facegym.adapters..")
            .allowEmptyShould(true);
}
```

- [ ] **Step 4: Rodar**

Run: `cd facegym-api && ./mvnw -q test -Dtest=ArquiteturaTest`
Expected: PASS (3 regras). Para provar que a regra morde, criar temporariamente `domain/Tmp.java` com `import org.springframework.stereotype.Component; @Component class Tmp {}`, rodar de novo → FAIL citando `Tmp`; apagar o arquivo.

- [ ] **Step 5: Commit**

```bash
git add facegym-api
git commit -m "feat(api): projeto Spring e regra de arquitetura hexagonal"
```

---

### Task 2: Domínio — CPF, regras e política de acesso

**Files:**
- Create: `facegym-api/src/main/java/com/facegym/domain/{Cpf,Aluno,Plano,Matricula,Acesso,ResultadoAcesso,MeioIdentificacao,Decisao,ContextoDeAcesso,RegraDeAcesso,Semana,PoliticaDeAcesso}.java`
- Create: `facegym-api/src/main/java/com/facegym/domain/regras/{BloqueioManual,PlanoAtivo,HorarioDoPlano,LimiteSemanal}.java`
- Test: `facegym-api/src/test/java/com/facegym/domain/CpfTest.java`, `PoliticaDeAcessoTest.java`

**Interfaces:**
- Produces:
  - `record Cpf(String valor)`; `static Cpf of(String entrada)` — aceita máscara, valida dígitos verificadores, lança `IllegalArgumentException("CPF inválido")`.
  - `class Aluno` — `Aluno(UUID id, String nome, Cpf cpf, String email, boolean bloqueado, String motivoBloqueio, Instant consentimentoBiometricoEm)`; `static Aluno novo(String nome, Cpf cpf, String email)`; getters `id() nome() cpf() email() bloqueado() motivoBloqueio() consentimentoBiometricoEm()`; `bloquear(String motivo)`, `desbloquear()`, `registrarConsentimento(Instant)`, `revogarConsentimento()`, `temConsentimento()`.
  - `record Plano(UUID id, String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim, Integer acessosPorSemana)`.
  - `record Matricula(UUID id, UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento)`; `boolean vigenteEm(LocalDate)`.
  - `record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score)`.
  - `enum ResultadoAcesso { LIBERADO, NEGADO }`, `enum MeioIdentificacao { FACIAL, CPF }`.
  - `sealed interface Decisao` com `record Libera()` e `record Nega(String motivo)`; `static Decisao libera()`, `static Decisao nega(String)`.
  - `record ContextoDeAcesso(Aluno aluno, Optional<Matricula> matricula, Optional<Plano> plano, LocalDateTime agora, long liberadosNaSemana)`.
  - `interface RegraDeAcesso { Decisao avaliar(ContextoDeAcesso c); }`
  - `final class Semana { static LocalDate inicio(LocalDate dia) }` — segunda-feira da semana ISO.
  - `class PoliticaDeAcesso` — `PoliticaDeAcesso()` com as 4 regras na ordem; `Decisao avaliar(ContextoDeAcesso)`.

- [ ] **Step 1: Testes de CPF (falhando)**

```java
// facegym-api/src/test/java/com/facegym/domain/CpfTest.java
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
```

Run: `./mvnw -q test -Dtest=CpfTest` → Expected: FAIL (compilação: `Cpf` não existe).

- [ ] **Step 2: Implementar Cpf**

```java
// facegym-api/src/main/java/com/facegym/domain/Cpf.java
package com.facegym.domain;

public record Cpf(String valor) {

    public Cpf {
        if (valor == null || !valor.matches("\\d{11}") || valor.chars().distinct().count() == 1 || !digitosOk(valor)) {
            throw new IllegalArgumentException("CPF inválido");
        }
    }

    public static Cpf of(String entrada) {
        if (entrada == null) throw new IllegalArgumentException("CPF inválido");
        return new Cpf(entrada.replaceAll("\\D", ""));
    }

    private static boolean digitosOk(String c) {
        return digito(c, 9) == c.charAt(9) - '0' && digito(c, 10) == c.charAt(10) - '0';
    }

    private static int digito(String c, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) soma += (c.charAt(i) - '0') * (n + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
```

Run: `./mvnw -q test -Dtest=CpfTest` → Expected: PASS (3).

- [ ] **Step 3: Tipos do domínio**

```java
// facegym-api/src/main/java/com/facegym/domain/Aluno.java
package com.facegym.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Aluno {
    private final UUID id;
    private final String nome;
    private final Cpf cpf;
    private final String email;
    private boolean bloqueado;
    private String motivoBloqueio;
    private Instant consentimentoBiometricoEm;

    public Aluno(UUID id, String nome, Cpf cpf, String email, boolean bloqueado, String motivoBloqueio,
                 Instant consentimentoBiometricoEm) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        this.id = Objects.requireNonNull(id);
        this.nome = nome.strip();
        this.cpf = Objects.requireNonNull(cpf);
        this.email = email;
        this.bloqueado = bloqueado;
        this.motivoBloqueio = motivoBloqueio;
        this.consentimentoBiometricoEm = consentimentoBiometricoEm;
    }

    public static Aluno novo(String nome, Cpf cpf, String email) {
        return new Aluno(UUID.randomUUID(), nome, cpf, email, false, null, null);
    }

    public void bloquear(String motivo) {
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Motivo do bloqueio é obrigatório");
        this.bloqueado = true;
        this.motivoBloqueio = motivo.strip();
    }

    public void desbloquear() {
        this.bloqueado = false;
        this.motivoBloqueio = null;
    }

    public void registrarConsentimento(Instant quando) { this.consentimentoBiometricoEm = Objects.requireNonNull(quando); }
    public void revogarConsentimento() { this.consentimentoBiometricoEm = null; }
    public boolean temConsentimento() { return consentimentoBiometricoEm != null; }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public Cpf cpf() { return cpf; }
    public String email() { return email; }
    public boolean bloqueado() { return bloqueado; }
    public String motivoBloqueio() { return motivoBloqueio; }
    public Instant consentimentoBiometricoEm() { return consentimentoBiometricoEm; }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/Plano.java
package com.facegym.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record Plano(UUID id, String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim,
                    Integer acessosPorSemana) {
    public Plano {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome do plano é obrigatório");
        if (dias == null || dias.isEmpty()) throw new IllegalArgumentException("Plano precisa de pelo menos um dia");
        if (!inicio.isBefore(fim)) throw new IllegalArgumentException("Horário inicial deve ser antes do final");
        if (acessosPorSemana != null && acessosPorSemana <= 0) throw new IllegalArgumentException("Limite semanal deve ser positivo");
        dias = Set.copyOf(dias);
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/Matricula.java
package com.facegym.domain;

import java.time.LocalDate;
import java.util.UUID;

public record Matricula(UUID id, UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento) {
    public Matricula {
        if (vencimento.isBefore(inicio)) throw new IllegalArgumentException("Vencimento antes do início");
    }

    /** Vencimento é inclusivo: vence dia 10, entra dia 10. */
    public boolean vigenteEm(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(vencimento);
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/ResultadoAcesso.java
package com.facegym.domain;

public enum ResultadoAcesso { LIBERADO, NEGADO }
```

```java
// facegym-api/src/main/java/com/facegym/domain/MeioIdentificacao.java
package com.facegym.domain;

public enum MeioIdentificacao { FACIAL, CPF }
```

```java
// facegym-api/src/main/java/com/facegym/domain/Acesso.java
package com.facegym.domain;

import java.time.Instant;
import java.util.UUID;

public record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                     MeioIdentificacao meio, Double score) {
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/Decisao.java
package com.facegym.domain;

public sealed interface Decisao {
    record Libera() implements Decisao {}
    record Nega(String motivo) implements Decisao {}

    static Decisao libera() { return new Libera(); }
    static Decisao nega(String motivo) { return new Nega(motivo); }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/ContextoDeAcesso.java
package com.facegym.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/** Tudo que as regras precisam, já resolvido; `agora` está no fuso da academia. */
public record ContextoDeAcesso(Aluno aluno, Optional<Matricula> matricula, Optional<Plano> plano,
                               LocalDateTime agora, long liberadosNaSemana) {
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/RegraDeAcesso.java
package com.facegym.domain;

public interface RegraDeAcesso {
    Decisao avaliar(ContextoDeAcesso c);
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/Semana.java
package com.facegym.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class Semana {
    private Semana() {}

    /** Segunda-feira da semana ISO que contém o dia. */
    public static LocalDate inicio(LocalDate dia) {
        return dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
```

- [ ] **Step 4: Testes da política (falhando)**

Plano "Manhã": segunda a sexta, 06:00–12:00, 3 acessos por semana. 2026-10-05 é uma segunda-feira.

```java
// facegym-api/src/test/java/com/facegym/domain/PoliticaDeAcessoTest.java
package com.facegym.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeAcessoTest {

    static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);
    final PoliticaDeAcesso politica = new PoliticaDeAcesso();
    final Plano manha = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 3);
    final Aluno aluno = Aluno.novo("Ana", Cpf.of("52998224725"), "ana@x.com");
    final Matricula matricula = new Matricula(UUID.randomUUID(), aluno.id(), manha.id(),
            SEGUNDA.minusDays(30), SEGUNDA.plusDays(4));

    ContextoDeAcesso ctx(LocalDateTime agora, long liberados) {
        return new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(manha), agora, liberados);
    }

    @Test
    void liberaDentroDoPlano() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(7, 30), 0))).isEqualTo(Decisao.libera());
    }

    @Test
    void horarioInicialEntraEFinalNaoEntra() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(6, 0), 0))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(11, 59), 0))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(12, 0), 0)))
                .isEqualTo(Decisao.nega("Plano Manhã: fora do horário (06:00–12:00)"));
    }

    @Test
    void negaDiaForaDoPlano() {
        LocalDateTime sabado = SEGUNDA.plusDays(5).atTime(8, 0);
        var semVencer = new ContextoDeAcesso(aluno, Optional.of(new Matricula(matricula.id(), aluno.id(), manha.id(),
                SEGUNDA, SEGUNDA.plusDays(30))), Optional.of(manha), sabado, 0);
        assertThat(politica.avaliar(semVencer)).isEqualTo(Decisao.nega("Plano Manhã: não vale no sábado"));
    }

    @Test
    void diaDoVencimentoAindaEntraEDiaSeguinteNao() {
        assertThat(politica.avaliar(ctx(SEGUNDA.plusDays(4).atTime(8, 0), 0))).isEqualTo(Decisao.libera());
        var depois = new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(manha),
                SEGUNDA.plusDays(7).atTime(8, 0), 0);
        assertThat(politica.avaliar(depois)).isEqualTo(Decisao.nega("Plano vencido ou inexistente"));
    }

    @Test
    void semMatriculaNega() {
        var semPlano = new ContextoDeAcesso(aluno, Optional.empty(), Optional.empty(), SEGUNDA.atTime(8, 0), 0);
        assertThat(politica.avaliar(semPlano)).isEqualTo(Decisao.nega("Plano vencido ou inexistente"));
    }

    @Test
    void limiteSemanalContaSoAteOLimite() {
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(8, 0), 2))).isEqualTo(Decisao.libera());
        assertThat(politica.avaliar(ctx(SEGUNDA.atTime(8, 0), 3)))
                .isEqualTo(Decisao.nega("Plano Manhã: limite de 3 acessos por semana atingido"));
    }

    @Test
    void semLimiteSemanalNuncaNegaPorQuantidade() {
        var livre = new Plano(manha.id(), "Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.MIN, LocalTime.MAX, null);
        var c = new ContextoDeAcesso(aluno, Optional.of(matricula), Optional.of(livre), SEGUNDA.atTime(8, 0), 999);
        assertThat(politica.avaliar(c)).isEqualTo(Decisao.libera());
    }

    @Test
    void bloqueioVemAntesDeTudo() {
        aluno.bloquear("Falta de atestado");
        var semPlano = new ContextoDeAcesso(aluno, Optional.empty(), Optional.empty(), SEGUNDA.atTime(3, 0), 99);
        assertThat(politica.avaliar(semPlano)).isEqualTo(Decisao.nega("Aluno bloqueado: Falta de atestado"));
    }

    @Test
    void semanaComecaNaSegunda() {
        assertThat(Semana.inicio(SEGUNDA.plusDays(6))).isEqualTo(SEGUNDA);   // domingo
        assertThat(Semana.inicio(SEGUNDA.plusDays(7))).isEqualTo(SEGUNDA.plusDays(7)); // próxima segunda
    }
}
```

Run: `./mvnw -q test -Dtest=PoliticaDeAcessoTest` → Expected: FAIL (compilação: `PoliticaDeAcesso` não existe).

- [ ] **Step 5: Regras e política**

```java
// facegym-api/src/main/java/com/facegym/domain/regras/BloqueioManual.java
package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.RegraDeAcesso;

public class BloqueioManual implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        return c.aluno().bloqueado() ? Decisao.nega("Aluno bloqueado: " + c.aluno().motivoBloqueio()) : Decisao.libera();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/regras/PlanoAtivo.java
package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.RegraDeAcesso;

public class PlanoAtivo implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        boolean vigente = c.plano().isPresent()
                && c.matricula().map(m -> m.vigenteEm(c.agora().toLocalDate())).orElse(false);
        return vigente ? Decisao.libera() : Decisao.nega("Plano vencido ou inexistente");
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/regras/HorarioDoPlano.java
package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.Plano;
import com.facegym.domain.RegraDeAcesso;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Pressupõe PlanoAtivo avaliada antes (plano presente). */
public class HorarioDoPlano implements RegraDeAcesso {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        Plano p = c.plano().orElseThrow();
        var dia = c.agora().getDayOfWeek();
        if (!p.dias().contains(dia)) {
            String nomeDia = dia.getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
            String artigo = (dia.getValue() >= 6) ? "no" : "na";
            return Decisao.nega("Plano " + p.nome() + ": não vale " + artigo + " " + nomeDia);
        }
        LocalTime hora = c.agora().toLocalTime();
        if (hora.isBefore(p.inicio()) || !hora.isBefore(p.fim())) {
            return Decisao.nega("Plano " + p.nome() + ": fora do horário ("
                    + p.inicio().format(HH_MM) + "–" + p.fim().format(HH_MM) + ")");
        }
        return Decisao.libera();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/regras/LimiteSemanal.java
package com.facegym.domain.regras;

import com.facegym.domain.ContextoDeAcesso;
import com.facegym.domain.Decisao;
import com.facegym.domain.Plano;
import com.facegym.domain.RegraDeAcesso;

/** Pressupõe PlanoAtivo avaliada antes (plano presente). */
public class LimiteSemanal implements RegraDeAcesso {
    @Override
    public Decisao avaliar(ContextoDeAcesso c) {
        Plano p = c.plano().orElseThrow();
        if (p.acessosPorSemana() != null && c.liberadosNaSemana() >= p.acessosPorSemana()) {
            return Decisao.nega("Plano " + p.nome() + ": limite de " + p.acessosPorSemana() + " acessos por semana atingido");
        }
        return Decisao.libera();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/domain/PoliticaDeAcesso.java
package com.facegym.domain;

import com.facegym.domain.regras.BloqueioManual;
import com.facegym.domain.regras.HorarioDoPlano;
import com.facegym.domain.regras.LimiteSemanal;
import com.facegym.domain.regras.PlanoAtivo;

import java.util.List;

public class PoliticaDeAcesso {
    // A ordem importa: a primeira negação é o motivo mostrado no totem.
    private final List<RegraDeAcesso> regras = List.of(
            new BloqueioManual(), new PlanoAtivo(), new HorarioDoPlano(), new LimiteSemanal());

    public Decisao avaliar(ContextoDeAcesso c) {
        for (RegraDeAcesso regra : regras) {
            Decisao d = regra.avaliar(c);
            if (d instanceof Decisao.Nega) return d;
        }
        return Decisao.libera();
    }
}
```

Nota: `LocalTime.MAX` como fim do plano "Livre" faz `isBefore(fim)` verdadeiro para qualquer hora até 23:59:59.999999998 — suficiente para o teste.

- [ ] **Step 6: Rodar**

Run: `./mvnw -q test -Dtest='CpfTest,PoliticaDeAcessoTest,ArquiteturaTest'`
Expected: PASS (todos).

- [ ] **Step 7: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): domínio com CPF, regras de acesso e política"
```

---

### Task 3: Caso de uso RealizarCheckIn e portas

**Files:**
- Create: `facegym-api/src/main/java/com/facegym/application/port/{ReconhecimentoFacial,Identificacao,ReconhecimentoIndisponivel,RostoNaoEncontrado,Alunos,Planos,Matriculas,RegistroDeAcessos,Relogio,CheckInsPendentes}.java`
- Create: `facegym-api/src/main/java/com/facegym/application/{Limiares,ResultadoCheckIn,RealizarCheckIn}.java`
- Create: `facegym-api/src/main/java/com/facegym/adapters/memoria/CheckInsPendentesEmMemoria.java`
- Test: `facegym-api/src/test/java/com/facegym/application/Fakes.java`, `RealizarCheckInTest.java`

**Interfaces:**
- Consumes: tudo do domínio (Task 2).
- Produces:
  - `interface ReconhecimentoFacial { Identificacao identificar(byte[] foto); Identificacao compararDemo(byte[] foto); void cadastrar(UUID alunoId, byte[] foto); void remover(UUID alunoId); }` — pode lançar `ReconhecimentoIndisponivel` (e `RostoNaoEncontrado` em `cadastrar`/imagem inválida).
  - `record Identificacao(UUID alunoId, Double score)`; `static Identificacao ninguem()`; `boolean encontrou()`.
  - `class ReconhecimentoIndisponivel extends RuntimeException` (`(String msg, Throwable causa)`), `class RostoNaoEncontrado extends RuntimeException` (`(String msg)`).
  - `interface Alunos { Optional<Aluno> porId(UUID); Optional<Aluno> porCpf(Cpf); void salvar(Aluno); List<Aluno> todos(); }`
  - `interface Planos { Optional<Plano> porId(UUID); void salvar(Plano); List<Plano> todos(); }`
  - `interface Matriculas { Optional<Matricula> vigente(UUID alunoId, LocalDate dia); void salvar(Matricula); }`
  - `interface RegistroDeAcessos { void registrar(Acesso); long liberadosDesde(UUID alunoId, Instant desde); List<Acesso> recentes(int limite); }`
  - `interface Relogio { Instant agora(); ZoneId fuso(); }`
  - `interface CheckInsPendentes { String criar(UUID alunoId, Double score, Instant expiraEm); Optional<Pendente> consumir(String token, Instant agora); record Pendente(UUID alunoId, Double score) {} }`
  - `record Limiares(double aceite, double duvida)`.
  - `sealed interface ResultadoCheckIn` com `Liberado(String nome)`, `Negado(String nome, String motivo)`, `ConfirmarCpf(String token)`, `NaoReconhecido()`, `BiometriaIndisponivel()`.
  - `class RealizarCheckIn(ReconhecimentoFacial, Alunos, Planos, Matriculas, RegistroDeAcessos, CheckInsPendentes, Relogio, Limiares)` com `porFoto(byte[])`, `confirmarCpf(String token, String cpf)`, `porCpf(String cpf)`, `demo(byte[]) -> Optional<String>` (nome, sem gravar nada).
  - `class CheckInsPendentesEmMemoria implements CheckInsPendentes`.

- [ ] **Step 1: Portas e tipos da aplicação**

```java
// facegym-api/src/main/java/com/facegym/application/port/Identificacao.java
package com.facegym.application.port;

import java.util.UUID;

public record Identificacao(UUID alunoId, Double score) {
    public static Identificacao ninguem() { return new Identificacao(null, null); }
    public boolean encontrou() { return alunoId != null && score != null; }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/ReconhecimentoFacial.java
package com.facegym.application.port;

import java.util.UUID;

public interface ReconhecimentoFacial {
    Identificacao identificar(byte[] foto);
    /** Igual a identificar, mas para a câmera de visitantes: nunca grava nada. */
    Identificacao compararDemo(byte[] foto);
    void cadastrar(UUID alunoId, byte[] foto);
    void remover(UUID alunoId);
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/ReconhecimentoIndisponivel.java
package com.facegym.application.port;

public class ReconhecimentoIndisponivel extends RuntimeException {
    public ReconhecimentoIndisponivel(String msg, Throwable causa) { super(msg, causa); }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/RostoNaoEncontrado.java
package com.facegym.application.port;

public class RostoNaoEncontrado extends RuntimeException {
    public RostoNaoEncontrado(String msg) { super(msg); }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/Alunos.java
package com.facegym.application.port;

import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Alunos {
    Optional<Aluno> porId(UUID id);
    Optional<Aluno> porCpf(Cpf cpf);
    void salvar(Aluno aluno);
    List<Aluno> todos();
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/Planos.java
package com.facegym.application.port;

import com.facegym.domain.Plano;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Planos {
    Optional<Plano> porId(UUID id);
    void salvar(Plano plano);
    List<Plano> todos();
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/Matriculas.java
package com.facegym.application.port;

import com.facegym.domain.Matricula;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface Matriculas {
    Optional<Matricula> vigente(UUID alunoId, LocalDate dia);
    void salvar(Matricula matricula);
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/RegistroDeAcessos.java
package com.facegym.application.port;

import com.facegym.domain.Acesso;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RegistroDeAcessos {
    void registrar(Acesso acesso);
    long liberadosDesde(UUID alunoId, Instant desde);
    List<Acesso> recentes(int limite);
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/Relogio.java
package com.facegym.application.port;

import java.time.Instant;
import java.time.ZoneId;

public interface Relogio {
    Instant agora();
    ZoneId fuso();
}
```

```java
// facegym-api/src/main/java/com/facegym/application/port/CheckInsPendentes.java
package com.facegym.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckInsPendentes {
    String criar(UUID alunoId, Double score, Instant expiraEm);
    /** Remove e devolve; vazio se não existe ou expirou. Uso único. */
    Optional<Pendente> consumir(String token, Instant agora);

    record Pendente(UUID alunoId, Double score) {}
}
```

```java
// facegym-api/src/main/java/com/facegym/application/Limiares.java
package com.facegym.application;

public record Limiares(double aceite, double duvida) {
    public Limiares {
        if (!(duvida < aceite)) throw new IllegalArgumentException("Limiar de dúvida deve ser menor que o de aceite");
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/ResultadoCheckIn.java
package com.facegym.application;

public sealed interface ResultadoCheckIn {
    record Liberado(String nome) implements ResultadoCheckIn {}
    record Negado(String nome, String motivo) implements ResultadoCheckIn {}
    record ConfirmarCpf(String token) implements ResultadoCheckIn {}
    record NaoReconhecido() implements ResultadoCheckIn {}
    record BiometriaIndisponivel() implements ResultadoCheckIn {}
}
```

- [ ] **Step 2: Fakes para os testes**

```java
// facegym-api/src/test/java/com/facegym/application/Fakes.java
package com.facegym.application;

import com.facegym.adapters.memoria.CheckInsPendentesEmMemoria;
import com.facegym.application.port.*;
import com.facegym.domain.*;

import java.time.*;
import java.util.*;

/** Implementações em memória das portas, para testar casos de uso sem Spring. */
public class Fakes {

    public static class ReconhecimentoFake implements ReconhecimentoFacial {
        public Identificacao proxima = Identificacao.ninguem();
        public boolean fora = false;
        public final Map<UUID, byte[]> cadastrados = new HashMap<>();

        public Identificacao identificar(byte[] foto) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            return proxima;
        }
        public Identificacao compararDemo(byte[] foto) { return identificar(foto); }
        public void cadastrar(UUID id, byte[] foto) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            cadastrados.put(id, foto);
        }
        public void remover(UUID id) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            cadastrados.remove(id);
        }
    }

    public static class AlunosFake implements Alunos {
        public final Map<UUID, Aluno> dados = new LinkedHashMap<>();
        public Optional<Aluno> porId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Aluno> porCpf(Cpf cpf) { return dados.values().stream().filter(a -> a.cpf().equals(cpf)).findFirst(); }
        public void salvar(Aluno a) { dados.put(a.id(), a); }
        public List<Aluno> todos() { return List.copyOf(dados.values()); }
    }

    public static class PlanosFake implements Planos {
        public final Map<UUID, Plano> dados = new LinkedHashMap<>();
        public Optional<Plano> porId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public void salvar(Plano p) { dados.put(p.id(), p); }
        public List<Plano> todos() { return List.copyOf(dados.values()); }
    }

    public static class MatriculasFake implements Matriculas {
        public final List<Matricula> dados = new ArrayList<>();
        public Optional<Matricula> vigente(UUID alunoId, LocalDate dia) {
            return dados.stream().filter(m -> m.alunoId().equals(alunoId) && m.vigenteEm(dia))
                    .max(Comparator.comparing(Matricula::vencimento));
        }
        public void salvar(Matricula m) { dados.add(m); }
    }

    public static class AcessosFake implements RegistroDeAcessos {
        public final List<Acesso> dados = new ArrayList<>();
        public void registrar(Acesso a) { dados.add(a); }
        public long liberadosDesde(UUID alunoId, Instant desde) {
            return dados.stream().filter(a -> alunoId.equals(a.alunoId()) && a.resultado() == ResultadoAcesso.LIBERADO
                    && !a.dataHora().isBefore(desde)).count();
        }
        public List<Acesso> recentes(int limite) {
            return dados.reversed().stream().limit(limite).toList();
        }
    }

    public static class RelogioFake implements Relogio {
        public Instant agora;
        public RelogioFake(LocalDateTime local) { set(local); }
        public void set(LocalDateTime local) { agora = local.atZone(fuso()).toInstant(); }
        public Instant agora() { return agora; }
        public ZoneId fuso() { return ZoneId.of("America/Sao_Paulo"); }
    }

    public final ReconhecimentoFake reconhecimento = new ReconhecimentoFake();
    public final AlunosFake alunos = new AlunosFake();
    public final PlanosFake planos = new PlanosFake();
    public final MatriculasFake matriculas = new MatriculasFake();
    public final AcessosFake acessos = new AcessosFake();
    public final CheckInsPendentesEmMemoria pendentes = new CheckInsPendentesEmMemoria();
    public final RelogioFake relogio = new RelogioFake(LocalDate.of(2026, 10, 5).atTime(8, 0)); // segunda 08:00

    public RealizarCheckIn checkIn() {
        return new RealizarCheckIn(reconhecimento, alunos, planos, matriculas, acessos, pendentes, relogio,
                new Limiares(0.41, 0.18));
    }
}
```

- [ ] **Step 3: Testes do check-in (falhando)**

```java
// facegym-api/src/test/java/com/facegym/application/RealizarCheckInTest.java
package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Identificacao;
import com.facegym.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealizarCheckInTest {

    static final byte[] FOTO = {1, 2, 3};
    static final String CPF_ANA = "529.982.247-25";
    Fakes f;
    RealizarCheckIn checkIn;
    Aluno ana;

    @BeforeEach
    void setUp() {
        f = new Fakes();
        checkIn = f.checkIn();
        Plano manha = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 2);
        f.planos.salvar(manha);
        ana = Aluno.novo("Ana", Cpf.of(CPF_ANA), null);
        f.alunos.salvar(ana);
        f.matriculas.salvar(new Matricula(UUID.randomUUID(), ana.id(), manha.id(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void scoreAltoLiberaERegistraComoFacial() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Liberado("Ana"));
        Acesso a = f.acessos.dados.getFirst();
        assertThat(a.resultado()).isEqualTo(ResultadoAcesso.LIBERADO);
        assertThat(a.meio()).isEqualTo(MeioIdentificacao.FACIAL);
        assertThat(a.score()).isEqualTo(0.62);
    }

    @Test
    void scoreNaFaixaDeDuvidaPedeCpfSemRegistrarAcesso() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(ConfirmarCpf.class);
        assertThat(f.acessos.dados).isEmpty();
    }

    @Test
    void confirmacaoComCpfCertoLibera() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new Liberado("Ana"));
        assertThat(f.acessos.dados.getFirst().meio()).isEqualTo(MeioIdentificacao.CPF);
    }

    @Test
    void confirmacaoComCpfDeOutraPessoaNaoLibera() {
        f.alunos.salvar(Aluno.novo("Beto", Cpf.of("11144477735"), null));
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        assertThat(checkIn.confirmarCpf(token, "111.444.777-35")).isEqualTo(new NaoReconhecido());
        assertThat(f.acessos.dados.getFirst().motivo()).isEqualTo("CPF não confere com o rosto");
    }

    @Test
    void tokenNaoPodeSerUsadoDuasVezes() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        checkIn.confirmarCpf(token, CPF_ANA);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void tokenExpiraEm60Segundos() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.30);
        var token = ((ConfirmarCpf) checkIn.porFoto(FOTO)).token();
        f.relogio.agora = f.relogio.agora.plusSeconds(61);
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void scoreBaixoOuNinguemNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.10);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
        f.reconhecimento.proxima = Identificacao.ninguem();
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaDeAlunoQueNaoExisteMaisNaoReconhece() {
        f.reconhecimento.proxima = new Identificacao(UUID.randomUUID(), 0.90);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaDoArCaiNoCpf() {
        f.reconhecimento.fora = true;
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new BiometriaIndisponivel());
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Liberado("Ana"));
    }

    @Test
    void cpfPassaPelasMesmasRegras() {
        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(13, 0));
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Negado("Ana", "Plano Manhã: fora do horário (06:00–12:00)"));
    }

    @Test
    void cpfNaoCadastrado() {
        assertThat(checkIn.porCpf("111.444.777-35")).isEqualTo(new NaoReconhecido());
    }

    @Test
    void limiteSemanalContaSoLiberadosDaSemanaAtual() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
        assertThat(checkIn.porFoto(FOTO)).isEqualTo(new Negado("Ana", "Plano Manhã: limite de 2 acessos por semana atingido"));
        f.relogio.set(LocalDate.of(2026, 10, 12).atTime(8, 0)); // segunda seguinte
        assertThat(checkIn.porFoto(FOTO)).isInstanceOf(Liberado.class);
    }

    @Test
    void demoDevolveNomeSemRegistrarNada() {
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.9);
        assertThat(checkIn.demo(FOTO)).contains("Ana");
        f.reconhecimento.proxima = new Identificacao(ana.id(), 0.2);
        assertThat(checkIn.demo(FOTO)).isEmpty();
        assertThat(f.acessos.dados).isEmpty();
    }
}
```

Run: `./mvnw -q test -Dtest=RealizarCheckInTest` → Expected: FAIL (compilação: `RealizarCheckIn`, `CheckInsPendentesEmMemoria` não existem).

- [ ] **Step 4: Implementar**

```java
// facegym-api/src/main/java/com/facegym/adapters/memoria/CheckInsPendentesEmMemoria.java
package com.facegym.adapters.memoria;

import com.facegym.application.port.CheckInsPendentes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pendências vivem 60 s; memória basta (instância única). */
public class CheckInsPendentesEmMemoria implements CheckInsPendentes {
    private record Entrada(Pendente pendente, Instant expiraEm) {}

    private final Map<String, Entrada> dados = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String criar(UUID alunoId, Double score, Instant expiraEm) {
        dados.values().removeIf(e -> e.expiraEm().isBefore(Instant.now().minusSeconds(300)));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        dados.put(token, new Entrada(new Pendente(alunoId, score), expiraEm));
        return token;
    }

    @Override
    public Optional<Pendente> consumir(String token, Instant agora) {
        if (token == null) return Optional.empty();
        Entrada e = dados.remove(token);
        if (e == null || agora.isAfter(e.expiraEm())) return Optional.empty();
        return Optional.of(e.pendente());
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/RealizarCheckIn.java
package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.*;
import com.facegym.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class RealizarCheckIn {
    static final Duration VALIDADE_CONFIRMACAO = Duration.ofSeconds(60);

    private final ReconhecimentoFacial reconhecimento;
    private final Alunos alunos;
    private final Planos planos;
    private final Matriculas matriculas;
    private final RegistroDeAcessos acessos;
    private final CheckInsPendentes pendentes;
    private final Relogio relogio;
    private final Limiares limiares;
    private final PoliticaDeAcesso politica = new PoliticaDeAcesso();

    public RealizarCheckIn(ReconhecimentoFacial reconhecimento, Alunos alunos, Planos planos, Matriculas matriculas,
                           RegistroDeAcessos acessos, CheckInsPendentes pendentes, Relogio relogio, Limiares limiares) {
        this.reconhecimento = reconhecimento;
        this.alunos = alunos;
        this.planos = planos;
        this.matriculas = matriculas;
        this.acessos = acessos;
        this.pendentes = pendentes;
        this.relogio = relogio;
        this.limiares = limiares;
    }

    public ResultadoCheckIn porFoto(byte[] foto) {
        Identificacao id;
        try {
            id = reconhecimento.identificar(foto);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null);
            return new BiometriaIndisponivel();
        }
        if (!id.encontrou() || id.score() < limiares.duvida()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, id.score());
            return new NaoReconhecido();
        }
        Optional<Aluno> aluno = alunos.porId(id.alunoId());
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Biometria sem aluno cadastrado", MeioIdentificacao.FACIAL, id.score());
            return new NaoReconhecido();
        }
        if (id.score() < limiares.aceite()) {
            return new ConfirmarCpf(pendentes.criar(id.alunoId(), id.score(), relogio.agora().plus(VALIDADE_CONFIRMACAO)));
        }
        return decidir(aluno.get(), MeioIdentificacao.FACIAL, id.score());
    }

    public ResultadoCheckIn confirmarCpf(String token, String cpf) {
        Cpf informado = Cpf.of(cpf);
        Optional<CheckInsPendentes.Pendente> pendente = pendentes.consumir(token, relogio.agora());
        if (pendente.isEmpty()) return new NaoReconhecido();
        Optional<Aluno> aluno = alunos.porId(pendente.get().alunoId());
        if (aluno.isEmpty() || !aluno.get().cpf().equals(informado)) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não confere com o rosto", MeioIdentificacao.CPF, pendente.get().score());
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, pendente.get().score());
    }

    public ResultadoCheckIn porCpf(String cpf) {
        Optional<Aluno> aluno = alunos.porCpf(Cpf.of(cpf));
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não cadastrado", MeioIdentificacao.CPF, null);
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, null);
    }

    /** Câmera de visitantes: só diz de quem é o rosto, sem registrar acesso. */
    public Optional<String> demo(byte[] foto) {
        Identificacao id = reconhecimento.compararDemo(foto);
        if (!id.encontrou() || id.score() < limiares.aceite()) return Optional.empty();
        return alunos.porId(id.alunoId()).map(Aluno::nome);
    }

    private ResultadoCheckIn decidir(Aluno aluno, MeioIdentificacao meio, Double score) {
        Instant agora = relogio.agora();
        LocalDateTime local = LocalDateTime.ofInstant(agora, relogio.fuso());
        Optional<Matricula> matricula = matriculas.vigente(aluno.id(), local.toLocalDate());
        Optional<Plano> plano = matricula.flatMap(m -> planos.porId(m.planoId()));
        Instant inicioSemana = Semana.inicio(local.toLocalDate()).atStartOfDay(relogio.fuso()).toInstant();
        long liberados = acessos.liberadosDesde(aluno.id(), inicioSemana);

        Decisao decisao = politica.avaliar(new ContextoDeAcesso(aluno, matricula, plano, local, liberados));
        if (decisao instanceof Decisao.Nega nega) {
            registrar(aluno.id(), ResultadoAcesso.NEGADO, nega.motivo(), meio, score);
            return new Negado(aluno.nome(), nega.motivo());
        }
        registrar(aluno.id(), ResultadoAcesso.LIBERADO, null, meio, score);
        return new Liberado(aluno.nome());
    }

    private void registrar(UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score) {
        acessos.registrar(new Acesso(UUID.randomUUID(), relogio.agora(), alunoId, resultado, motivo, meio, score));
    }
}
```

- [ ] **Step 5: Rodar**

Run: `./mvnw -q test -Dtest='RealizarCheckInTest,ArquiteturaTest'`
Expected: PASS (13 + 3).

- [ ] **Step 6: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): caso de uso de check-in com limiares, confirmação por CPF e fallback"
```

---

### Task 4: Gestão de alunos, planos e biometria

**Files:**
- Create: `facegym-api/src/main/java/com/facegym/application/{NaoEncontrado,ConsentimentoAusente,GestaoDeAlunos,GestaoDePlanos}.java`
- Test: `facegym-api/src/test/java/com/facegym/application/GestaoDeAlunosTest.java`

**Interfaces:**
- Consumes: portas e `Fakes` (Task 3).
- Produces:
  - `class NaoEncontrado extends RuntimeException(String o)` — mensagem `"<o> não encontrado"`.
  - `class ConsentimentoAusente extends RuntimeException`.
  - `class GestaoDeAlunos(Alunos, ReconhecimentoFacial, Relogio)`: `Aluno cadastrar(String nome, String cpf, String email)`, `List<Aluno> listar()`, `void bloquear(UUID, String motivo)`, `void desbloquear(UUID)`, `void registrarConsentimento(UUID)`, `void cadastrarBiometria(UUID, byte[] foto)`, `void removerBiometria(UUID)`.
  - `class GestaoDePlanos(Planos, Alunos, Matriculas)`: `Plano cadastrar(String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim, Integer acessosPorSemana)`, `List<Plano> listar()`, `Matricula matricular(UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento)`.

- [ ] **Step 1: Testes (falhando)**

```java
// facegym-api/src/test/java/com/facegym/application/GestaoDeAlunosTest.java
package com.facegym.application;

import com.facegym.application.port.ReconhecimentoIndisponivel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestaoDeAlunosTest {

    final Fakes f = new Fakes();
    final GestaoDeAlunos gestao = new GestaoDeAlunos(f.alunos, f.reconhecimento, f.relogio);
    final GestaoDePlanos planos = new GestaoDePlanos(f.planos, f.alunos, f.matriculas);
    final byte[] foto = {9};

    @Test
    void biometriaExigeConsentimento() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> gestao.cadastrarBiometria(ana.id(), foto)).isInstanceOf(ConsentimentoAusente.class);
        assertThat(f.reconhecimento.cadastrados).isEmpty();

        gestao.registrarConsentimento(ana.id());
        gestao.cadastrarBiometria(ana.id(), foto);
        assertThat(f.reconhecimento.cadastrados).containsKey(ana.id());
    }

    @Test
    void removerBiometriaApagaVetorERevogaConsentimento() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.registrarConsentimento(ana.id());
        gestao.cadastrarBiometria(ana.id(), foto);

        gestao.removerBiometria(ana.id());

        assertThat(f.reconhecimento.cadastrados).isEmpty();
        assertThat(f.alunos.porId(ana.id()).orElseThrow().temConsentimento()).isFalse();
    }

    @Test
    void seBiometriaEstaForaDoArConsentimentoNaoERevogado() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.registrarConsentimento(ana.id());
        f.reconhecimento.fora = true;
        assertThatThrownBy(() -> gestao.removerBiometria(ana.id())).isInstanceOf(ReconhecimentoIndisponivel.class);
        assertThat(f.alunos.porId(ana.id()).orElseThrow().temConsentimento()).isTrue();
    }

    @Test
    void cpfDuplicadoERejeitado() {
        gestao.cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> gestao.cadastrar("Outra", "52998224725", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CPF já cadastrado");
    }

    @Test
    void bloquearEDesbloquear() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        gestao.bloquear(ana.id(), "Falta de atestado");
        assertThat(f.alunos.porId(ana.id()).orElseThrow().bloqueado()).isTrue();
        gestao.desbloquear(ana.id());
        assertThat(f.alunos.porId(ana.id()).orElseThrow().bloqueado()).isFalse();
    }

    @Test
    void alunoInexistente() {
        assertThatThrownBy(() -> gestao.bloquear(UUID.randomUUID(), "x")).isInstanceOf(NaoEncontrado.class);
    }

    @Test
    void matriculaExigeAlunoEPlanoExistentes() {
        var ana = gestao.cadastrar("Ana", "529.982.247-25", null);
        var livre = planos.cadastrar("Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(5, 0), LocalTime.of(23, 0), null);
        var m = planos.matricular(ana.id(), livre.id(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(f.matriculas.vigente(ana.id(), LocalDate.of(2026, 10, 15))).contains(m);
        assertThatThrownBy(() -> planos.matricular(ana.id(), UUID.randomUUID(), LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NaoEncontrado.class);
    }
}
```

Run: `./mvnw -q test -Dtest=GestaoDeAlunosTest` → Expected: FAIL (compilação).

- [ ] **Step 2: Implementar**

```java
// facegym-api/src/main/java/com/facegym/application/NaoEncontrado.java
package com.facegym.application;

public class NaoEncontrado extends RuntimeException {
    public NaoEncontrado(String oque) { super(oque + " não encontrado"); }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/ConsentimentoAusente.java
package com.facegym.application;

public class ConsentimentoAusente extends RuntimeException {
    public ConsentimentoAusente() { super("Aluno não deu consentimento para uso de biometria"); }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/GestaoDeAlunos.java
package com.facegym.application;

import com.facegym.application.port.Alunos;
import com.facegym.application.port.ReconhecimentoFacial;
import com.facegym.application.port.Relogio;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;

import java.util.List;
import java.util.UUID;

public class GestaoDeAlunos {
    private final Alunos alunos;
    private final ReconhecimentoFacial reconhecimento;
    private final Relogio relogio;

    public GestaoDeAlunos(Alunos alunos, ReconhecimentoFacial reconhecimento, Relogio relogio) {
        this.alunos = alunos;
        this.reconhecimento = reconhecimento;
        this.relogio = relogio;
    }

    public Aluno cadastrar(String nome, String cpf, String email) {
        Cpf c = Cpf.of(cpf);
        if (alunos.porCpf(c).isPresent()) throw new IllegalArgumentException("CPF já cadastrado");
        Aluno a = Aluno.novo(nome, c, email);
        alunos.salvar(a);
        return a;
    }

    public List<Aluno> listar() { return alunos.todos(); }

    public void bloquear(UUID id, String motivo) {
        Aluno a = buscar(id);
        a.bloquear(motivo);
        alunos.salvar(a);
    }

    public void desbloquear(UUID id) {
        Aluno a = buscar(id);
        a.desbloquear();
        alunos.salvar(a);
    }

    public void registrarConsentimento(UUID id) {
        Aluno a = buscar(id);
        a.registrarConsentimento(relogio.agora());
        alunos.salvar(a);
    }

    public void cadastrarBiometria(UUID id, byte[] foto) {
        Aluno a = buscar(id);
        if (!a.temConsentimento()) throw new ConsentimentoAusente();
        reconhecimento.cadastrar(a.id(), foto);
    }

    /** Apaga o vetor primeiro: se a biometria estiver fora, o consentimento continua valendo. */
    public void removerBiometria(UUID id) {
        Aluno a = buscar(id);
        reconhecimento.remover(a.id());
        a.revogarConsentimento();
        alunos.salvar(a);
    }

    private Aluno buscar(UUID id) {
        return alunos.porId(id).orElseThrow(() -> new NaoEncontrado("Aluno"));
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/GestaoDePlanos.java
package com.facegym.application;

import com.facegym.application.port.Alunos;
import com.facegym.application.port.Matriculas;
import com.facegym.application.port.Planos;
import com.facegym.domain.Matricula;
import com.facegym.domain.Plano;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GestaoDePlanos {
    private final Planos planos;
    private final Alunos alunos;
    private final Matriculas matriculas;

    public GestaoDePlanos(Planos planos, Alunos alunos, Matriculas matriculas) {
        this.planos = planos;
        this.alunos = alunos;
        this.matriculas = matriculas;
    }

    public Plano cadastrar(String nome, BigDecimal preco, Set<DayOfWeek> dias, LocalTime inicio, LocalTime fim,
                           Integer acessosPorSemana) {
        Plano p = new Plano(UUID.randomUUID(), nome, preco, dias, inicio, fim, acessosPorSemana);
        planos.salvar(p);
        return p;
    }

    public List<Plano> listar() { return planos.todos(); }

    public Matricula matricular(UUID alunoId, UUID planoId, LocalDate inicio, LocalDate vencimento) {
        alunos.porId(alunoId).orElseThrow(() -> new NaoEncontrado("Aluno"));
        planos.porId(planoId).orElseThrow(() -> new NaoEncontrado("Plano"));
        Matricula m = new Matricula(UUID.randomUUID(), alunoId, planoId, inicio, vencimento);
        matriculas.salvar(m);
        return m;
    }
}
```

- [ ] **Step 3: Rodar**

Run: `./mvnw -q test -Dtest='GestaoDeAlunosTest,ArquiteturaTest'` → Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): gestão de alunos, planos, matrículas e biometria com consentimento"
```

---

### Task 5: Persistência JDBC com Flyway

**Files:**
- Create: `facegym-api/src/main/resources/db/migration/V1__schema.sql`
- Create: `facegym-api/src/main/resources/application.yml` (parte de datasource/flyway; completada na Task 7)
- Create: `facegym-api/src/main/java/com/facegym/adapters/jdbc/{AlunosJdbc,PlanosJdbc,MatriculasJdbc,AcessosJdbc}.java`
- Test: `facegym-api/src/test/java/com/facegym/adapters/jdbc/JdbcAdaptersTest.java`

**Interfaces:**
- Consumes: portas `Alunos`, `Planos`, `Matriculas`, `RegistroDeAcessos` (Task 3).
- Produces: `@Repository` `AlunosJdbc(JdbcClient)`, `PlanosJdbc(JdbcClient)`, `MatriculasJdbc(JdbcClient)`, `AcessosJdbc(JdbcClient)`; tabela `admin(id, email, senha_hash)` usada na Task 7.

- [ ] **Step 1: Migration**

```sql
-- facegym-api/src/main/resources/db/migration/V1__schema.sql
CREATE TABLE aluno (
    id                           uuid PRIMARY KEY,
    nome                         varchar(120) NOT NULL,
    cpf                          char(11)     NOT NULL UNIQUE,
    email                        varchar(160),
    bloqueado                    boolean      NOT NULL DEFAULT false,
    motivo_bloqueio              varchar(200),
    consentimento_biometrico_em  timestamptz
);

CREATE TABLE plano (
    id              uuid PRIMARY KEY,
    nome            varchar(80)   NOT NULL,
    preco           numeric(10,2) NOT NULL,
    dias_semana     varchar(80)   NOT NULL,
    hora_inicio     time          NOT NULL,
    hora_fim        time          NOT NULL,
    acessos_semana  integer CHECK (acessos_semana > 0)
);

CREATE TABLE matricula (
    id          uuid PRIMARY KEY,
    aluno_id    uuid NOT NULL REFERENCES aluno(id),
    plano_id    uuid NOT NULL REFERENCES plano(id),
    inicio      date NOT NULL,
    vencimento  date NOT NULL,
    CHECK (vencimento >= inicio)
);
CREATE INDEX matricula_aluno_idx ON matricula (aluno_id, vencimento);

CREATE TABLE acesso (
    id         uuid PRIMARY KEY,
    data_hora  timestamptz NOT NULL,
    aluno_id   uuid REFERENCES aluno(id),
    resultado  varchar(10) NOT NULL,
    motivo     varchar(200),
    meio       varchar(10) NOT NULL,
    score      double precision
);
CREATE INDEX acesso_aluno_idx ON acesso (aluno_id, data_hora);
CREATE INDEX acesso_data_idx ON acesso (data_hora DESC);

CREATE TABLE admin (
    id          uuid PRIMARY KEY,
    email       varchar(160) NOT NULL UNIQUE,
    senha_hash  varchar(100) NOT NULL
);
```

```yaml
# facegym-api/src/main/resources/application.yml
spring:
  application:
    name: facegym-api
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5434}/${DB_NAME:facegym}
    username: ${DB_USER:facegym}
    password: ${DB_PASSWORD:facegym}
  flyway:
    enabled: true
```

- [ ] **Step 2: Testes (falhando)**

```java
// facegym-api/src/test/java/com/facegym/adapters/jdbc/JdbcAdaptersTest.java
package com.facegym.adapters.jdbc;

import com.facegym.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AlunosJdbc.class, PlanosJdbc.class, MatriculasJdbc.class, AcessosJdbc.class})
class JdbcAdaptersTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AlunosJdbc alunos;
    @Autowired PlanosJdbc planos;
    @Autowired MatriculasJdbc matriculas;
    @Autowired AcessosJdbc acessos;

    @Test
    void alunoIdaEVoltaIncluindoBloqueioEConsentimento() {
        Aluno a = Aluno.novo("Ana", Cpf.of("52998224725"), "ana@x.com");
        alunos.salvar(a);
        a.bloquear("Atestado");
        a.registrarConsentimento(Instant.parse("2026-10-01T10:00:00Z"));
        alunos.salvar(a); // upsert

        Aluno lido = alunos.porCpf(Cpf.of("529.982.247-25")).orElseThrow();
        assertThat(lido.id()).isEqualTo(a.id());
        assertThat(lido.bloqueado()).isTrue();
        assertThat(lido.motivoBloqueio()).isEqualTo("Atestado");
        assertThat(lido.consentimentoBiometricoEm()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(alunos.todos()).hasSize(1);
    }

    @Test
    void planoGuardaDiasEHorarios() {
        Plano p = new Plano(UUID.randomUUID(), "Manhã", new BigDecimal("89.90"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(6, 0), LocalTime.of(12, 0), 3);
        planos.salvar(p);
        assertThat(planos.porId(p.id())).contains(p);
    }

    @Test
    void matriculaVigenteRespeitaVencimentoInclusivo() {
        Aluno a = Aluno.novo("Beto", Cpf.of("11144477735"), null);
        alunos.salvar(a);
        Plano p = new Plano(UUID.randomUUID(), "Livre", BigDecimal.TEN, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(5, 0), LocalTime.of(23, 0), null);
        planos.salvar(p);
        Matricula m = new Matricula(UUID.randomUUID(), a.id(), p.id(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        matriculas.salvar(m);

        assertThat(matriculas.vigente(a.id(), LocalDate.of(2026, 10, 10))).contains(m);
        assertThat(matriculas.vigente(a.id(), LocalDate.of(2026, 10, 11))).isEmpty();
    }

    @Test
    void acessosContamSoLiberadosDesdeOInstante() {
        Aluno a = Aluno.novo("Caio", Cpf.of("39053344705"), null);
        alunos.salvar(a);
        Instant t = Instant.parse("2026-10-05T11:00:00Z");
        acessos.registrar(new Acesso(UUID.randomUUID(), t.minusSeconds(60), a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.FACIAL, 0.9));
        acessos.registrar(new Acesso(UUID.randomUUID(), t, a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.CPF, null));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(60), a.id(), ResultadoAcesso.NEGADO, "x", MeioIdentificacao.FACIAL, 0.5));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(90), null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, null));

        assertThat(acessos.liberadosDesde(a.id(), t)).isEqualTo(1);
        assertThat(acessos.recentes(2)).extracting(Acesso::motivo).containsExactly("Rosto não reconhecido", "x");
    }
}
```

Run: `./mvnw -q test -Dtest=JdbcAdaptersTest` → Expected: FAIL (compilação: adaptadores não existem).

- [ ] **Step 3: Adaptadores**

```java
// facegym-api/src/main/java/com/facegym/adapters/jdbc/AlunosJdbc.java
package com.facegym.adapters.jdbc;

import com.facegym.application.port.Alunos;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AlunosJdbc implements Alunos {
    private static final RowMapper<Aluno> MAPPER = (rs, i) -> {
        Timestamp consentimento = rs.getTimestamp("consentimento_biometrico_em");
        return new Aluno(rs.getObject("id", UUID.class), rs.getString("nome"), new Cpf(rs.getString("cpf")),
                rs.getString("email"), rs.getBoolean("bloqueado"), rs.getString("motivo_bloqueio"),
                consentimento == null ? null : consentimento.toInstant());
    };

    private final JdbcClient jdbc;

    public AlunosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Aluno> porId(UUID id) {
        return jdbc.sql("SELECT * FROM aluno WHERE id = ?").param(id).query(MAPPER).optional();
    }

    @Override
    public Optional<Aluno> porCpf(Cpf cpf) {
        return jdbc.sql("SELECT * FROM aluno WHERE cpf = ?").param(cpf.valor()).query(MAPPER).optional();
    }

    @Override
    public void salvar(Aluno a) {
        jdbc.sql("""
                INSERT INTO aluno (id, nome, cpf, email, bloqueado, motivo_bloqueio, consentimento_biometrico_em)
                VALUES (:id, :nome, :cpf, :email, :bloqueado, :motivo, :consentimento)
                ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, email = EXCLUDED.email,
                  bloqueado = EXCLUDED.bloqueado, motivo_bloqueio = EXCLUDED.motivo_bloqueio,
                  consentimento_biometrico_em = EXCLUDED.consentimento_biometrico_em""")
                .param("id", a.id()).param("nome", a.nome()).param("cpf", a.cpf().valor()).param("email", a.email())
                .param("bloqueado", a.bloqueado()).param("motivo", a.motivoBloqueio())
                .param("consentimento", a.consentimentoBiometricoEm() == null ? null : Timestamp.from(a.consentimentoBiometricoEm()))
                .update();
    }

    @Override
    public List<Aluno> todos() {
        return jdbc.sql("SELECT * FROM aluno ORDER BY nome").query(MAPPER).list();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/jdbc/PlanosJdbc.java
package com.facegym.adapters.jdbc;

import com.facegym.application.port.Planos;
import com.facegym.domain.Plano;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class PlanosJdbc implements Planos {
    private static final RowMapper<Plano> MAPPER = (rs, i) -> new Plano(
            rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBigDecimal("preco"),
            Arrays.stream(rs.getString("dias_semana").split(",")).map(DayOfWeek::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class))),
            rs.getTime("hora_inicio").toLocalTime(), rs.getTime("hora_fim").toLocalTime(),
            (Integer) rs.getObject("acessos_semana"));

    private final JdbcClient jdbc;

    public PlanosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Plano> porId(UUID id) {
        return jdbc.sql("SELECT * FROM plano WHERE id = ?").param(id).query(MAPPER).optional();
    }

    @Override
    public void salvar(Plano p) {
        String dias = p.dias().stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        jdbc.sql("""
                INSERT INTO plano (id, nome, preco, dias_semana, hora_inicio, hora_fim, acessos_semana)
                VALUES (:id, :nome, :preco, :dias, :inicio, :fim, :limite)
                ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, preco = EXCLUDED.preco,
                  dias_semana = EXCLUDED.dias_semana, hora_inicio = EXCLUDED.hora_inicio,
                  hora_fim = EXCLUDED.hora_fim, acessos_semana = EXCLUDED.acessos_semana""")
                .param("id", p.id()).param("nome", p.nome()).param("preco", p.preco()).param("dias", dias)
                .param("inicio", p.inicio()).param("fim", p.fim()).param("limite", p.acessosPorSemana())
                .update();
    }

    @Override
    public List<Plano> todos() {
        return jdbc.sql("SELECT * FROM plano ORDER BY nome").query(MAPPER).list();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/jdbc/MatriculasJdbc.java
package com.facegym.adapters.jdbc;

import com.facegym.application.port.Matriculas;
import com.facegym.domain.Matricula;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MatriculasJdbc implements Matriculas {
    private static final RowMapper<Matricula> MAPPER = (rs, i) -> new Matricula(
            rs.getObject("id", UUID.class), rs.getObject("aluno_id", UUID.class), rs.getObject("plano_id", UUID.class),
            rs.getObject("inicio", LocalDate.class), rs.getObject("vencimento", LocalDate.class));

    private final JdbcClient jdbc;

    public MatriculasJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Matricula> vigente(UUID alunoId, LocalDate dia) {
        return jdbc.sql("""
                SELECT * FROM matricula WHERE aluno_id = ? AND inicio <= ? AND vencimento >= ?
                ORDER BY vencimento DESC LIMIT 1""")
                .param(alunoId).param(dia).param(dia).query(MAPPER).optional();
    }

    @Override
    public void salvar(Matricula m) {
        jdbc.sql("INSERT INTO matricula (id, aluno_id, plano_id, inicio, vencimento) VALUES (?, ?, ?, ?, ?)")
                .param(m.id()).param(m.alunoId()).param(m.planoId()).param(m.inicio()).param(m.vencimento())
                .update();
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/jdbc/AcessosJdbc.java
package com.facegym.adapters.jdbc;

import com.facegym.application.port.RegistroDeAcessos;
import com.facegym.domain.Acesso;
import com.facegym.domain.MeioIdentificacao;
import com.facegym.domain.ResultadoAcesso;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AcessosJdbc implements RegistroDeAcessos {
    private static final RowMapper<Acesso> MAPPER = (rs, i) -> new Acesso(
            rs.getObject("id", UUID.class), rs.getTimestamp("data_hora").toInstant(),
            rs.getObject("aluno_id", UUID.class), ResultadoAcesso.valueOf(rs.getString("resultado")),
            rs.getString("motivo"), MeioIdentificacao.valueOf(rs.getString("meio")),
            (Double) rs.getObject("score"));

    private final JdbcClient jdbc;

    public AcessosJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void registrar(Acesso a) {
        jdbc.sql("INSERT INTO acesso (id, data_hora, aluno_id, resultado, motivo, meio, score) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .param(a.id()).param(Timestamp.from(a.dataHora())).param(a.alunoId()).param(a.resultado().name())
                .param(a.motivo()).param(a.meio().name()).param(a.score())
                .update();
    }

    @Override
    public long liberadosDesde(UUID alunoId, Instant desde) {
        return jdbc.sql("SELECT count(*) FROM acesso WHERE aluno_id = ? AND resultado = 'LIBERADO' AND data_hora >= ?")
                .param(alunoId).param(Timestamp.from(desde)).query(Long.class).single();
    }

    @Override
    public List<Acesso> recentes(int limite) {
        return jdbc.sql("SELECT * FROM acesso ORDER BY data_hora DESC LIMIT ?").param(limite).query(MAPPER).list();
    }
}
```

- [ ] **Step 4: Rodar**

Run: `./mvnw -q test -Dtest='JdbcAdaptersTest,ArquiteturaTest'` (Docker aberto) → Expected: PASS (4 + 3).

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): persistência JDBC com Flyway"
```

---

### Task 6: Cliente da biometria com timeout, retry e circuit breaker

**Files:**
- Create: `facegym-api/src/main/java/com/facegym/adapters/biometria/{BiometriaProperties,BiometriaHttpClient}.java`
- Test: `facegym-api/src/test/java/com/facegym/adapters/biometria/BiometriaHttpClientTest.java`

**Interfaces:**
- Consumes: `ReconhecimentoFacial`, `Identificacao`, `ReconhecimentoIndisponivel`, `RostoNaoEncontrado` (Task 3); contrato HTTP do plano 1 (Task 5).
- Produces:
  - `@ConfigurationProperties("facegym.biometria") record BiometriaProperties(String url, String chave, Duration timeout, int janela, float taxaFalha, Duration espera)`.
  - `class BiometriaHttpClient implements ReconhecimentoFacial` — `BiometriaHttpClient(BiometriaProperties, CircuitBreakerRegistry)`; `CircuitBreaker circuitBreaker()` (para health/métricas); nome do circuito `"biometria"`.

- [ ] **Step 1: Testes com WireMock (falhando)**

```java
// facegym-api/src/test/java/com/facegym/adapters/biometria/BiometriaHttpClientTest.java
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
    void demoUsaRotaPropria() {
        bio.stubFor(post("/faces/compare-demo").willReturn(okJson("{\"alunoId\":null,\"score\":null}")));
        client.compararDemo(FOTO);
        bio.verify(postRequestedFor(urlEqualTo("/faces/compare-demo")));
    }
}
```

Run: `./mvnw -q test -Dtest=BiometriaHttpClientTest` → Expected: FAIL (compilação).

- [ ] **Step 2: Implementar**

```java
// facegym-api/src/main/java/com/facegym/adapters/biometria/BiometriaProperties.java
package com.facegym.adapters.biometria;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param janela    chamadas na janela do circuit breaker
 * @param taxaFalha % de falhas que abre o circuito
 * @param espera    tempo aberto antes da meia-abertura
 */
@ConfigurationProperties("facegym.biometria")
public record BiometriaProperties(String url, String chave, Duration timeout, int janela, float taxaFalha,
                                  Duration espera) {
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/biometria/BiometriaHttpClient.java
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

    private record IdentifyResponse(UUID alunoId, Double score) {}

    private final RestClient http;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public BiometriaHttpClient(BiometriaProperties props, CircuitBreakerRegistry registry) {
        var jdk = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
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
                    .body(multipart(foto)).retrieve().onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RostoNaoEncontrado(detalhe(new String(res.getBody().readAllBytes())));
                    }).toBodilessEntity();
            return null;
        });
    }

    @Override
    public void remover(UUID alunoId) {
        protegido(() -> {
            http.delete().uri("/faces/{id}", alunoId).retrieve().toBodilessEntity();
            return null;
        });
    }

    private Identificacao post(String rota, byte[] foto) {
        IdentifyResponse r = http.post().uri(rota).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(foto)).retrieve().onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new RostoNaoEncontrado(detalhe(new String(res.getBody().readAllBytes())));
                }).body(IdentifyResponse.class);
        return r == null ? Identificacao.ninguem() : new Identificacao(r.alunoId(), r.score());
    }

    private <T> T protegido(Supplier<T> chamada) {
        Supplier<T> decorada = CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, chamada));
        try {
            return decorada.get();
        } catch (RostoNaoEncontrado e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ReconhecimentoIndisponivel("Serviço de biometria indisponível", e);
        }
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
```

Nota: `detalhe` extrai `detail` sem depender de Jackson no caminho de erro.

- [ ] **Step 3: Rodar**

Run: `./mvnw -q test -Dtest=BiometriaHttpClientTest` → Expected: PASS (9).

- [ ] **Step 4: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): cliente da biometria com timeout, retry e circuit breaker"
```

---

### Task 7: API REST, segurança do painel e teste do fluxo completo

**Files:**
- Modify: `facegym-api/src/main/resources/application.yml`
- Create: `facegym-api/src/main/java/com/facegym/adapters/config/{UseCaseConfig,SecurityConfig,AdminBootstrap,RelogioDoSistema}.java`
- Create: `facegym-api/src/main/java/com/facegym/adapters/web/{CheckInController,AdminController,AuthController,ErrosHandler}.java`
- Test: `facegym-api/src/test/java/com/facegym/FluxoCompletoIT.java`

**Interfaces:**
- Consumes: casos de uso (Tasks 3–4), adaptadores (Tasks 5–6).
- Produces (contrato HTTP usado pelo plano 3):
  - Público: `POST /api/v1/check-ins` (multipart `foto`), `POST /api/v1/check-ins/cpf` `{cpf}`, `POST /api/v1/check-ins/{token}/cpf` `{cpf}` → `{status, nome?, motivo?, token?}` com `status ∈ LIBERADO|NEGADO|CONFIRMAR_CPF|NAO_RECONHECIDO|BIOMETRIA_INDISPONIVEL`; `POST /api/v1/demo/identificar` (multipart `foto`) → `{nome: string|null}`; `POST /api/v1/auth/login` `{email, senha}` → `{token}`.
  - Admin (Bearer JWT): `GET/POST /api/v1/alunos`, `POST /api/v1/alunos/{id}/bloqueio {motivo}`, `DELETE /api/v1/alunos/{id}/bloqueio`, `POST /api/v1/alunos/{id}/consentimento`, `PUT /api/v1/alunos/{id}/biometria` (multipart `foto`), `DELETE /api/v1/alunos/{id}/biometria`, `GET/POST /api/v1/planos`, `POST /api/v1/matriculas`, `GET /api/v1/acessos?limite=50`.
  - Erros: `{mensagem}` com 400 (validação/CPF inválido), 401, 404, 409 (consentimento ausente, CPF duplicado), 413 (foto > 5 MB), 422 (sem rosto/imagem inválida), 503 (biometria fora, no painel).

- [ ] **Step 1: Configuração completa**

```yaml
# facegym-api/src/main/resources/application.yml
spring:
  application:
    name: facegym-api
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5434}/${DB_NAME:facegym}
    username: ${DB_USER:facegym}
    password: ${DB_PASSWORD:facegym}
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

server:
  port: ${PORT:8080}

facegym:
  fuso: ${FACEGYM_FUSO:America/Sao_Paulo}
  limiares:
    aceite: ${LIMIAR_ACEITE:0.41}
    duvida: ${LIMIAR_DUVIDA:0.18}
  jwt-secret: ${JWT_SECRET:dev-secret-troque-em-producao-com-32-bytes-ou-mais}
  admin:
    email: ${ADMIN_EMAIL:admin@facegym.dev}
    senha: ${ADMIN_PASSWORD:admin12345}
  cors: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
  biometria:
    url: ${BIOMETRIA_URL:http://localhost:8000}
    chave: ${BIOMETRIA_KEY:local-dev-key-0123456789}
    timeout: 2s
    janela: 10
    taxa-falha: 50
    espera: 30s

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 2: Beans dos casos de uso e relógio**

```java
// facegym-api/src/main/java/com/facegym/adapters/config/RelogioDoSistema.java
package com.facegym.adapters.config;

import com.facegym.application.port.Relogio;

import java.time.Instant;
import java.time.ZoneId;

public record RelogioDoSistema(ZoneId fuso) implements Relogio {
    @Override public Instant agora() { return Instant.now(); }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/config/UseCaseConfig.java
package com.facegym.adapters.config;

import com.facegym.adapters.biometria.BiometriaHttpClient;
import com.facegym.adapters.biometria.BiometriaProperties;
import com.facegym.adapters.memoria.CheckInsPendentesEmMemoria;
import com.facegym.application.*;
import com.facegym.application.port.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class UseCaseConfig {

    @Bean
    Relogio relogio(@Value("${facegym.fuso}") String fuso) {
        return new RelogioDoSistema(ZoneId.of(fuso));
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    BiometriaHttpClient reconhecimentoFacial(BiometriaProperties props, CircuitBreakerRegistry registry) {
        return new BiometriaHttpClient(props, registry);
    }

    /** Mostra o estado do circuito sem nunca deixar a aplicação DOWN (o Render reiniciaria). */
    @Bean
    HealthIndicator biometriaHealth(BiometriaHttpClient biometria) {
        return () -> {
            CircuitBreaker.State estado = biometria.circuitBreaker().getState();
            return Health.up().withDetail("circuito", estado.name()).build();
        };
    }

    @Bean
    CheckInsPendentes checkInsPendentes() { return new CheckInsPendentesEmMemoria(); }

    @Bean
    RealizarCheckIn realizarCheckIn(ReconhecimentoFacial r, Alunos a, Planos p, Matriculas m, RegistroDeAcessos ac,
                                    CheckInsPendentes pend, Relogio rel,
                                    @Value("${facegym.limiares.aceite}") double aceite,
                                    @Value("${facegym.limiares.duvida}") double duvida) {
        return new RealizarCheckIn(r, a, p, m, ac, pend, rel, new Limiares(aceite, duvida));
    }

    @Bean
    GestaoDeAlunos gestaoDeAlunos(Alunos a, ReconhecimentoFacial r, Relogio rel) {
        return new GestaoDeAlunos(a, r, rel);
    }

    @Bean
    GestaoDePlanos gestaoDePlanos(Planos p, Alunos a, Matriculas m) {
        return new GestaoDePlanos(p, a, m);
    }
}
```

- [ ] **Step 3: Segurança (JWT HS256) e bootstrap do admin**

```java
// facegym-api/src/main/java/com/facegym/adapters/config/SecurityConfig.java
package com.facegym.adapters.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, @Value("${facegym.cors}") String cors) throws Exception {
        var corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of(cors.split(",")));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return http
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(source))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/check-ins/**", "/api/v1/demo/identificar",
                                "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {}))
                .build();
    }

    @Bean
    SecretKeySpec jwtKey(@Value("${facegym.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET precisa de pelo menos 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/config/AdminBootstrap.java
package com.facegym.adapters.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Cria o admin inicial a partir de ADMIN_EMAIL/ADMIN_PASSWORD se ainda não existir nenhum. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String senha;

    public AdminBootstrap(JdbcClient jdbc, PasswordEncoder encoder,
                          @Value("${facegym.admin.email}") String email, @Value("${facegym.admin.senha}") String senha) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        long admins = jdbc.sql("SELECT count(*) FROM admin").query(Long.class).single();
        if (admins == 0) {
            jdbc.sql("INSERT INTO admin (id, email, senha_hash) VALUES (?, ?, ?)")
                    .param(UUID.randomUUID()).param(email.toLowerCase()).param(encoder.encode(senha)).update();
        }
    }
}
```

- [ ] **Step 4: Controllers e tratamento de erros**

```java
// facegym-api/src/main/java/com/facegym/adapters/web/CheckInController.java
package com.facegym.adapters.web;

import com.facegym.application.RealizarCheckIn;
import com.facegym.application.ResultadoCheckIn;
import com.facegym.application.ResultadoCheckIn.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CheckInController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resposta(String status, String nome, String motivo, String token) {
        static Resposta de(ResultadoCheckIn r) {
            return switch (r) {
                case Liberado l -> new Resposta("LIBERADO", l.nome(), null, null);
                case Negado n -> new Resposta("NEGADO", n.nome(), n.motivo(), null);
                case ConfirmarCpf c -> new Resposta("CONFIRMAR_CPF", null, null, c.token());
                case NaoReconhecido x -> new Resposta("NAO_RECONHECIDO", null, null, null);
                case BiometriaIndisponivel x -> new Resposta("BIOMETRIA_INDISPONIVEL", null, null, null);
            };
        }
    }

    public record CpfRequest(@NotBlank String cpf) {}

    private final RealizarCheckIn checkIn;

    public CheckInController(RealizarCheckIn checkIn) { this.checkIn = checkIn; }

    @PostMapping("/check-ins")
    public Resposta porFoto(@RequestParam("foto") MultipartFile foto) throws IOException {
        return Resposta.de(checkIn.porFoto(foto.getBytes()));
    }

    @PostMapping("/check-ins/cpf")
    public Resposta porCpf(@Valid @RequestBody CpfRequest req) {
        return Resposta.de(checkIn.porCpf(req.cpf()));
    }

    @PostMapping("/check-ins/{token}/cpf")
    public Resposta confirmar(@PathVariable String token, @Valid @RequestBody CpfRequest req) {
        return Resposta.de(checkIn.confirmarCpf(token, req.cpf()));
    }

    @PostMapping("/demo/identificar")
    public Map<String, String> demo(@RequestParam("foto") MultipartFile foto) throws IOException {
        var nome = checkIn.demo(foto.getBytes()).orElse(null);
        var body = new java.util.HashMap<String, String>();
        body.put("nome", nome);
        return body;
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/web/AdminController.java
package com.facegym.adapters.web;

import com.facegym.application.GestaoDeAlunos;
import com.facegym.application.GestaoDePlanos;
import com.facegym.application.port.RegistroDeAcessos;
import com.facegym.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    public record AlunoRequest(@NotBlank @Size(max = 120) String nome, @NotBlank String cpf, @Email String email) {}
    public record AlunoResponse(UUID id, String nome, String cpf, String email, boolean bloqueado,
                                String motivoBloqueio, Instant consentimentoBiometricoEm) {
        static AlunoResponse de(Aluno a) {
            return new AlunoResponse(a.id(), a.nome(), a.cpf().valor(), a.email(), a.bloqueado(),
                    a.motivoBloqueio(), a.consentimentoBiometricoEm());
        }
    }
    public record BloqueioRequest(@NotBlank @Size(max = 200) String motivo) {}
    public record PlanoRequest(@NotBlank @Size(max = 80) String nome, @NotNull @PositiveOrZero BigDecimal preco,
                               @NotEmpty Set<DayOfWeek> dias, @NotNull LocalTime inicio, @NotNull LocalTime fim,
                               @Positive Integer acessosPorSemana) {}
    public record MatriculaRequest(@NotNull UUID alunoId, @NotNull UUID planoId, @NotNull LocalDate inicio,
                                   @NotNull LocalDate vencimento) {}

    private final GestaoDeAlunos alunos;
    private final GestaoDePlanos planos;
    private final RegistroDeAcessos acessos;

    public AdminController(GestaoDeAlunos alunos, GestaoDePlanos planos, RegistroDeAcessos acessos) {
        this.alunos = alunos;
        this.planos = planos;
        this.acessos = acessos;
    }

    @GetMapping("/alunos")
    public List<AlunoResponse> listarAlunos() { return alunos.listar().stream().map(AlunoResponse::de).toList(); }

    @PostMapping("/alunos")
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoResponse cadastrarAluno(@Valid @RequestBody AlunoRequest r) {
        return AlunoResponse.de(alunos.cadastrar(r.nome(), r.cpf(), r.email()));
    }

    @PostMapping("/alunos/{id}/bloqueio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloquear(@PathVariable UUID id, @Valid @RequestBody BloqueioRequest r) { alunos.bloquear(id, r.motivo()); }

    @DeleteMapping("/alunos/{id}/bloqueio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@PathVariable UUID id) { alunos.desbloquear(id); }

    @PostMapping("/alunos/{id}/consentimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consentimento(@PathVariable UUID id) { alunos.registrarConsentimento(id); }

    @PutMapping("/alunos/{id}/biometria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cadastrarBiometria(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto) throws IOException {
        alunos.cadastrarBiometria(id, foto.getBytes());
    }

    @DeleteMapping("/alunos/{id}/biometria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBiometria(@PathVariable UUID id) { alunos.removerBiometria(id); }

    @GetMapping("/planos")
    public List<Plano> listarPlanos() { return planos.listar(); }

    @PostMapping("/planos")
    @ResponseStatus(HttpStatus.CREATED)
    public Plano cadastrarPlano(@Valid @RequestBody PlanoRequest r) {
        return planos.cadastrar(r.nome(), r.preco(), r.dias(), r.inicio(), r.fim(), r.acessosPorSemana());
    }

    @PostMapping("/matriculas")
    @ResponseStatus(HttpStatus.CREATED)
    public Matricula matricular(@Valid @RequestBody MatriculaRequest r) {
        return planos.matricular(r.alunoId(), r.planoId(), r.inicio(), r.vencimento());
    }

    @GetMapping("/acessos")
    public List<Acesso> acessos(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite) {
        return acessos.recentes(Math.min(Math.max(limite, 1), 500));
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/web/AuthController.java
package com.facegym.adapters.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {}

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;

    public AuthController(JdbcClient jdbc, PasswordEncoder encoder, JwtEncoder jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest r) {
        String hash = jdbc.sql("SELECT senha_hash FROM admin WHERE email = ?").param(r.email().toLowerCase())
                .query(String.class).optional().orElse(null);
        if (hash == null || !encoder.matches(r.senha(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }
        Instant agora = Instant.now();
        var claims = JwtClaimsSet.builder().subject(r.email().toLowerCase()).issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofHours(8))).claim("scope", "ADMIN").build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return Map.of("token", jwt.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/web/ErrosHandler.java
package com.facegym.adapters.web;

import com.facegym.application.ConsentimentoAusente;
import com.facegym.application.NaoEncontrado;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ErrosHandler {

    private static ResponseEntity<Map<String, String>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        HttpStatus s = "CPF já cadastrado".equals(e.getMessage()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return erro(s, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
        var campo = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("Requisição inválida");
        return erro(HttpStatus.BAD_REQUEST, campo);
    }

    @ExceptionHandler(NaoEncontrado.class)
    ResponseEntity<Map<String, String>> naoEncontrado(NaoEncontrado e) { return erro(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(ConsentimentoAusente.class)
    ResponseEntity<Map<String, String>> consentimento(ConsentimentoAusente e) { return erro(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) { return erro(HttpStatus.CONFLICT, "Registro duplicado"); }

    @ExceptionHandler(RostoNaoEncontrado.class)
    ResponseEntity<Map<String, String>> semRosto(RostoNaoEncontrado e) { return erro(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }

    @ExceptionHandler(ReconhecimentoIndisponivel.class)
    ResponseEntity<Map<String, String>> indisponivel(ReconhecimentoIndisponivel e) {
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Reconhecimento facial indisponível, tente em instantes");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> grande(MaxUploadSizeExceededException e) {
        return erro(HttpStatus.PAYLOAD_TOO_LARGE, "Foto maior que 5 MB");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return erro(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }
}
```

- [ ] **Step 5: Teste do fluxo completo (falhando até o Step 4 existir; escrito aqui para rodar ao fim)**

```java
// facegym-api/src/test/java/com/facegym/FluxoCompletoIT.java
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
        bio.stubFor(put("/faces/" + aluno).willReturn(noContent()));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/alunos/" + aluno + "/biometria").file(foto).header("Authorization", auth))
                .andExpect(status().isNoContent());

        bio.stubFor(post("/faces/identify").willReturn(okJson("{\"alunoId\":\"" + aluno + "\",\"score\":0.7}")));
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
        bio.stubFor(post("/faces/identify").willReturn(serverError()));
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
```


- [ ] **Step 6: Rodar toda a suíte**

Run: `./mvnw -q verify` (Docker aberto)
Expected: todos os testes unitários + `FluxoCompletoIT` (5) passam; `BUILD SUCCESS`.

- [ ] **Step 7: Teste manual contra a biometria real**

```bash
docker compose up -d  # da raiz, após a Task 8; antes dela, subir Postgres na 5434 e a biometria na 8000 manualmente
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@facegym.dev","senha":"admin12345"}'
```

Expected: `{"token":"..."}`.

- [ ] **Step 8: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): endpoints do totem e do painel, JWT e teste de fluxo completo"
```

---

### Task 8: Docker, docker-compose com os 3 serviços e CI

**Files:**
- Create: `facegym-api/Dockerfile`, `facegym-api/.dockerignore`
- Create: `docker-compose.yml` (raiz)
- Create: `.github/workflows/api.yml`

- [ ] **Step 1: Dockerfile**

```dockerfile
# facegym-api/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline -B
COPY src ./src
RUN mvn -q package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```
# facegym-api/.dockerignore
target
.mvn/wrapper/maven-wrapper.jar
```

- [ ] **Step 2: docker-compose na raiz**

```yaml
# docker-compose.yml
services:
  api-db:
    image: postgres:16-alpine
    environment: { POSTGRES_DB: facegym, POSTGRES_USER: facegym, POSTGRES_PASSWORD: facegym }
    ports: ["5434:5432"]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U facegym"], interval: 5s, retries: 10 }

  biometria-db:
    image: pgvector/pgvector:pg16
    environment: { POSTGRES_DB: biometria, POSTGRES_USER: biometria, POSTGRES_PASSWORD: biometria }
    ports: ["5433:5432"]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U biometria"], interval: 5s, retries: 10 }

  biometria:
    build: ./facegym-biometria
    environment:
      INTERNAL_KEY: local-dev-key-0123456789
      DATABASE_URL: postgresql://biometria:biometria@biometria-db:5432/biometria
    depends_on: { biometria-db: { condition: service_healthy } }
    ports: ["8000:8000"]
    mem_limit: 512m

  api:
    build: ./facegym-api
    environment:
      DB_HOST: api-db
      DB_PORT: 5432
      BIOMETRIA_URL: http://biometria:8000
      BIOMETRIA_KEY: local-dev-key-0123456789
    depends_on: { api-db: { condition: service_healthy }, biometria: { condition: service_started } }
    ports: ["8080:8080"]
    mem_limit: 512m
```

- [ ] **Step 3: Subir e testar de ponta a ponta com um rosto de verdade**

```bash
docker compose up -d --build
until curl -sf localhost:8080/actuator/health >/dev/null; do sleep 3; done
T=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@facegym.dev","senha":"admin12345"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
P=$(curl -s -X POST localhost:8080/api/v1/planos -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"nome":"Livre","preco":99,"dias":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],"inicio":"00:00","fim":"23:59"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
A=$(curl -s -X POST localhost:8080/api/v1/alunos -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"nome":"Pierre","cpf":"529.982.247-25"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
curl -s -X POST localhost:8080/api/v1/matriculas -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d "{\"alunoId\":\"$A\",\"planoId\":\"$P\",\"inicio\":\"2026-01-01\",\"vencimento\":\"2027-01-01\"}" >/dev/null
curl -s -X POST localhost:8080/api/v1/alunos/$A/consentimento -H "Authorization: Bearer $T"
curl -s -o /dev/null -w "biometria %{http_code}\n" -X PUT localhost:8080/api/v1/alunos/$A/biometria -H "Authorization: Bearer $T" -F foto=@/tmp/claude-501/face-small.jpg
curl -s -X POST localhost:8080/api/v1/check-ins -F foto=@/tmp/claude-501/face-small.jpg
docker compose down
```

Expected: `biometria 204` e `{"status":"LIBERADO","nome":"Pierre"}`. (`/tmp/claude-501/face-small.jpg` é a foto usada no plano 1; se não existir, baixar de novo e reduzir com `sips -Z 1200`.)

- [ ] **Step 4: CI**

```yaml
# .github/workflows/api.yml
name: api
on:
  push:
    paths: ["facegym-api/**", ".github/workflows/api.yml"]
  pull_request:
    paths: ["facegym-api/**"]
jobs:
  verify:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: facegym-api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - run: ./mvnw -B verify
```

- [ ] **Step 5: Commit**

```bash
git add facegym-api/Dockerfile facegym-api/.dockerignore docker-compose.yml .github/workflows/api.yml
git commit -m "chore(api): Dockerfile, docker-compose com os 3 serviços e CI"
```
