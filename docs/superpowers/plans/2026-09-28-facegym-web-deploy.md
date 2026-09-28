# FaceGym Web, Demo e Deploy — Implementation Plan (3 de 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fluxo "Teste com você" (visitante temporário) na API, dados de demo com rostos gerados no Canva, front React (totem + painel) e deploy público (Vercel + Render + Neon).

**Architecture:** O visitante temporário é um caso de uso novo (`VisitantesTemporarios`) sobre as mesmas portas; a expiração roda num `@Scheduled` do adaptador. O front é um SPA Vite: o totem é público, o painel usa o JWT da API. Deploy: API e biometria como web services Docker no Render, bancos em dois databases de um projeto Neon, front na Vercel.

**Tech Stack:** Spring Boot 3.3 (plano 2), FastAPI (plano 1), React 19 + TypeScript + Vite + Tailwind 4 + React Router, Vitest, Canva (geração de imagens), Pillow (variações), Render Blueprint, Neon, Vercel.

**Spec:** `docs/superpowers/specs/2026-09-28-facegym-design.md` (seções 2.3, 2.4, 6, 8). Contratos: plano 2 Task 7 (API) e plano 1 Task 5 (biometria).

## Global Constraints

- Visitante: consentimento obrigatório; expira em **10 min**; máximo **20 criados por hora** (429); só o embedding é gravado.
- Plano "Visitante": todos os dias, 00:00–23:59, sem limite semanal; id fixo `00000000-0000-4000-8000-000000000001`.
- Nunca guardar a foto (nem na API, nem no front além da memória da página).
- `VITE_API_URL` na Vercel como variável **não sensível** (lição do physiomanage-web).
- Nenhum segredo com valor padrão em produção (`JWT_SECRET` gerado pelo Render; `ADMIN_PASSWORD` definido pelo usuário).
- Credenciais da demo publicadas no README são só as do painel de demonstração.

## Review Focus

1. **Visitante tenta check-in depois de expirar** — deve dar "não reconhecido", não 500 (biometria e aluno já apagados). Teste na Task 1.
2. **Cadastro do visitante com foto sem rosto** — não pode sobrar aluno "fantasma" sem biometria contando no limite por hora. Teste na Task 1.
3. **Biometria fora do ar durante a expiração** — o visitante não pode ser apagado só de um lado; tenta de novo no minuto seguinte. Teste na Task 1.
4. **Câmera negada ou inexistente no navegador** — o totem mostra mensagem e mantém a galeria funcionando. Teste manual na Task 5.
5. **Primeira requisição com os serviços dormindo (Render free)** — o totem avisa "acordando" e aquece a biometria antes do primeiro check-in. Endpoint na Task 2, uso na Task 4.

---

### Task 1: Caso de uso do visitante temporário

**Files:**
- Modify: `facegym-api/src/main/java/com/facegym/domain/Cpf.java`
- Modify: `facegym-api/src/main/java/com/facegym/application/port/Alunos.java`
- Create: `facegym-api/src/main/java/com/facegym/application/port/Visitantes.java`
- Create: `facegym-api/src/main/java/com/facegym/application/{LimiteDeVisitantes,VisitantesTemporarios}.java`
- Modify: `facegym-api/src/test/java/com/facegym/application/Fakes.java`
- Test: `facegym-api/src/test/java/com/facegym/domain/CpfTest.java`, `facegym-api/src/test/java/com/facegym/application/VisitantesTemporariosTest.java`

**Interfaces:**
- Consumes: `Aluno`, `Matricula`, `Cpf` (plano 2 Task 2); portas `Alunos`, `Matriculas`, `ReconhecimentoFacial`, `Relogio` (plano 2 Task 3); `ConsentimentoAusente`, `NaoEncontrado` (plano 2 Task 4).
- Produces:
  - `static Cpf Cpf.aleatorio(java.util.random.RandomGenerator r)`.
  - `Alunos.remover(UUID id)` — apaga o aluno (matrículas, acessos e visitante em cascata no banco).
  - `interface Visitantes { void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm); boolean existe(UUID alunoId); List<UUID> expiradosAte(Instant agora); long criadosDesde(Instant desde); }`
  - `class LimiteDeVisitantes extends RuntimeException`.
  - `class VisitantesTemporarios(Alunos, Matriculas, Visitantes, ReconhecimentoFacial, Relogio)` com `static final UUID PLANO_VISITANTE`, `static final Duration VALIDADE = 10 min`, `static final int MAX_POR_HORA = 20`, `record Visitante(UUID id, String nome, String cpf, Instant expiraEm)`, `Visitante criar(byte[] foto, boolean consentimento)`, `void remover(UUID id)`, `int expirar()`.

- [ ] **Step 1: Teste do CPF aleatório (falhando)** — adicionar em `CpfTest`:

```java
    @Test
    void aleatorioGeraCpfValido() {
        var r = new java.util.Random(42);
        for (int i = 0; i < 200; i++) {
            Cpf c = Cpf.aleatorio(r);
            assertThat(Cpf.of(c.valor())).isEqualTo(c);
        }
    }
```

Run: `./mvnw -q test -Dtest=CpfTest` → Expected: FAIL (compilação: `aleatorio` não existe).

- [ ] **Step 2: Implementar** — adicionar em `Cpf`:

```java
    /** CPF fictício válido (dígitos verificadores corretos), para visitantes da demo. */
    public static Cpf aleatorio(java.util.random.RandomGenerator r) {
        while (true) {
            StringBuilder base = new StringBuilder();
            for (int i = 0; i < 9; i++) base.append(r.nextInt(10));
            String nove = base.toString();
            String dez = nove + digito(nove + "00", 9);
            String onze = dez + digito(dez + "0", 10);
            if (onze.chars().distinct().count() > 1) return new Cpf(onze);
        }
    }
```

Run: `./mvnw -q test -Dtest=CpfTest` → Expected: PASS (4).

- [ ] **Step 3: Portas**

```java
// facegym-api/src/main/java/com/facegym/application/port/Visitantes.java
package com.facegym.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface Visitantes {
    void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm);
    boolean existe(UUID alunoId);
    List<UUID> expiradosAte(Instant agora);
    long criadosDesde(Instant desde);
}
```

Em `Alunos`, adicionar:

```java
    /** Apaga o aluno; matrículas, acessos e registro de visitante vão junto (cascata no banco). */
    void remover(UUID id);
```

Em `Fakes.AlunosFake`, adicionar:

```java
        public void remover(UUID id) { dados.remove(id); }
```

E em `Fakes`, a classe e o campo:

```java
    public static class VisitantesFake implements Visitantes {
        public record Registro(Instant criadoEm, Instant expiraEm) {}
        public final Map<UUID, Registro> dados = new LinkedHashMap<>();
        public final List<Instant> criacoes = new ArrayList<>(); // histórico para o limite por hora
        public void registrar(UUID id, Instant criadoEm, Instant expiraEm) {
            dados.put(id, new Registro(criadoEm, expiraEm));
            criacoes.add(criadoEm);
        }
        public boolean existe(UUID id) { return dados.containsKey(id); }
        public List<UUID> expiradosAte(Instant agora) {
            return dados.entrySet().stream().filter(e -> !e.getValue().expiraEm().isAfter(agora)).map(Map.Entry::getKey).toList();
        }
        public long criadosDesde(Instant desde) { return criacoes.stream().filter(c -> !c.isBefore(desde)).count(); }
    }

    public final VisitantesFake visitantes = new VisitantesFake();
```

Nota: no banco o limite conta linhas de `visitante`; como visitantes expirados são apagados, o limite por hora conta só os ainda vivos **mais** os apagados na última hora seria o ideal. Para simplificar e manter a regra honesta, `visitante` guarda `criado_em` e a remoção **não** apaga a linha de `visitante_log` (Task 2). O fake reproduz isso com `criacoes`.

Também em `Fakes.visitantes`, `remover` do aluno deve remover o visitante (cascata do banco): alterar `AlunosFake.remover` para receber o fake de visitantes não é possível sem acoplamento, então o caso de uso não depende disso — `existe` é checado antes de remover e o teste valida pelo fake de alunos.

- [ ] **Step 4: Testes do caso de uso (falhando)**

```java
// facegym-api/src/test/java/com/facegym/application/VisitantesTemporariosTest.java
package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Identificacao;
import com.facegym.application.port.ReconhecimentoIndisponivel;
import com.facegym.application.port.RostoNaoEncontrado;
import com.facegym.domain.Plano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitantesTemporariosTest {

    Fakes f;
    VisitantesTemporarios visitantes;
    final byte[] selfie = {7};

    @BeforeEach
    void setUp() {
        f = new Fakes();
        f.planos.salvar(new Plano(VisitantesTemporarios.PLANO_VISITANTE, "Visitante", BigDecimal.ZERO,
                EnumSet.allOf(DayOfWeek.class), LocalTime.MIN, LocalTime.of(23, 59), null));
        visitantes = new VisitantesTemporarios(f.alunos, f.matriculas, f.visitantes, f.reconhecimento, f.relogio);
    }

    @Test
    void criaVisitanteComConsentimentoBiometriaEMatricula() {
        var v = visitantes.criar(selfie, true);

        assertThat(v.nome()).startsWith("Visitante ");
        assertThat(v.expiraEm()).isEqualTo(f.relogio.agora().plus(VisitantesTemporarios.VALIDADE));
        assertThat(f.alunos.porId(v.id()).orElseThrow().temConsentimento()).isTrue();
        assertThat(f.reconhecimento.cadastrados).containsKey(v.id());

        f.reconhecimento.proxima = new Identificacao(v.id(), 0.9);
        assertThat(f.checkIn().porFoto(selfie)).isEqualTo(new Liberado(v.nome()));
    }

    @Test
    void semConsentimentoNaoCriaNada() {
        assertThatThrownBy(() -> visitantes.criar(selfie, false)).isInstanceOf(ConsentimentoAusente.class);
        assertThat(f.alunos.dados).isEmpty();
        assertThat(f.visitantes.criacoes).isEmpty();
    }

    @Test
    void fotoSemRostoNaoDeixaAlunoFantasmaNemContaNoLimite() {
        var falha = new Fakes.ReconhecimentoFake() {
            @Override public void cadastrar(java.util.UUID id, byte[] foto) { throw new RostoNaoEncontrado("nenhum rosto encontrado"); }
        };
        var v = new VisitantesTemporarios(f.alunos, f.matriculas, f.visitantes, falha, f.relogio);
        assertThatThrownBy(() -> v.criar(selfie, true)).isInstanceOf(RostoNaoEncontrado.class);
        assertThat(f.alunos.dados).isEmpty();
        assertThat(f.visitantes.criacoes).isEmpty();
    }

    @Test
    void limiteDe20PorHora() {
        for (int i = 0; i < VisitantesTemporarios.MAX_POR_HORA; i++) visitantes.criar(selfie, true);
        assertThatThrownBy(() -> visitantes.criar(selfie, true)).isInstanceOf(LimiteDeVisitantes.class);

        f.relogio.agora = f.relogio.agora.plusSeconds(3601);
        visitantes.criar(selfie, true);
    }

    @Test
    void expiraDepoisDe10MinutosEDepoisNaoEReconhecido() {
        var v = visitantes.criar(selfie, true);
        f.relogio.agora = f.relogio.agora.plus(VisitantesTemporarios.VALIDADE).minusSeconds(1);
        assertThat(visitantes.expirar()).isZero();

        f.relogio.agora = f.relogio.agora.plusSeconds(1);
        assertThat(visitantes.expirar()).isEqualTo(1);
        assertThat(f.alunos.porId(v.id())).isEmpty();
        assertThat(f.reconhecimento.cadastrados).doesNotContainKey(v.id());

        // vetor órfão improvável, mas se a biometria ainda devolver o id: não reconhecido
        f.reconhecimento.proxima = new Identificacao(v.id(), 0.9);
        assertThat(f.checkIn().porFoto(selfie)).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaNaExpiracaoMantemAlunoParaTentarDeNovo() {
        var v = visitantes.criar(selfie, true);
        f.relogio.agora = f.relogio.agora.plus(VisitantesTemporarios.VALIDADE);
        f.reconhecimento.fora = true;
        assertThat(visitantes.expirar()).isZero();
        assertThat(f.alunos.porId(v.id())).isPresent();

        f.reconhecimento.fora = false;
        assertThat(visitantes.expirar()).isEqualTo(1);
    }

    @Test
    void removerNaHoraSoFuncionaParaVisitante() {
        var v = visitantes.criar(selfie, true);
        visitantes.remover(v.id());
        assertThat(f.alunos.porId(v.id())).isEmpty();

        var aluno = new GestaoDeAlunos(f.alunos, f.reconhecimento, f.relogio).cadastrar("Ana", "529.982.247-25", null);
        assertThatThrownBy(() -> visitantes.remover(aluno.id())).isInstanceOf(NaoEncontrado.class);
        assertThat(f.alunos.porId(aluno.id())).isPresent();
    }
}
```

Run: `./mvnw -q test -Dtest=VisitantesTemporariosTest` → Expected: FAIL (compilação).

- [ ] **Step 5: Implementar**

```java
// facegym-api/src/main/java/com/facegym/application/LimiteDeVisitantes.java
package com.facegym.application;

public class LimiteDeVisitantes extends RuntimeException {
    public LimiteDeVisitantes() { super("Muitos testes na última hora, tente mais tarde"); }
}
```

```java
// facegym-api/src/main/java/com/facegym/application/VisitantesTemporarios.java
package com.facegym.application;

import com.facegym.application.port.*;
import com.facegym.domain.Aluno;
import com.facegym.domain.Cpf;
import com.facegym.domain.Matricula;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/** "Teste com você": aluno temporário com consentimento, apagado em 10 minutos. */
public class VisitantesTemporarios {
    public static final UUID PLANO_VISITANTE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final Duration VALIDADE = Duration.ofMinutes(10);
    public static final int MAX_POR_HORA = 20;

    public record Visitante(UUID id, String nome, String cpf, Instant expiraEm) {}

    private final Alunos alunos;
    private final Matriculas matriculas;
    private final Visitantes visitantes;
    private final ReconhecimentoFacial reconhecimento;
    private final Relogio relogio;
    private final SecureRandom random = new SecureRandom();

    public VisitantesTemporarios(Alunos alunos, Matriculas matriculas, Visitantes visitantes,
                                 ReconhecimentoFacial reconhecimento, Relogio relogio) {
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.visitantes = visitantes;
        this.reconhecimento = reconhecimento;
        this.relogio = relogio;
    }

    public Visitante criar(byte[] foto, boolean consentimento) {
        if (!consentimento) throw new ConsentimentoAusente();
        Instant agora = relogio.agora();
        if (visitantes.criadosDesde(agora.minus(Duration.ofHours(1))) >= MAX_POR_HORA) throw new LimiteDeVisitantes();

        Cpf cpf;
        do { cpf = Cpf.aleatorio(random); } while (alunos.porCpf(cpf).isPresent());
        byte[] sufixo = new byte[2];
        random.nextBytes(sufixo);
        Aluno aluno = new Aluno(UUID.randomUUID(), "Visitante " + HexFormat.of().formatHex(sufixo).toUpperCase(),
                cpf, null, false, null, agora);
        alunos.salvar(aluno);

        try {
            reconhecimento.cadastrar(aluno.id(), foto);
        } catch (RuntimeException e) {
            alunos.remover(aluno.id()); // sem biometria não existe visitante
            throw e;
        }

        LocalDate hoje = LocalDate.ofInstant(agora, relogio.fuso());
        matriculas.salvar(new Matricula(UUID.randomUUID(), aluno.id(), PLANO_VISITANTE, hoje, hoje.plusDays(1)));
        Instant expiraEm = agora.plus(VALIDADE);
        visitantes.registrar(aluno.id(), agora, expiraEm);
        return new Visitante(aluno.id(), aluno.nome(), cpf.valor(), expiraEm);
    }

    public void remover(UUID id) {
        if (!visitantes.existe(id)) throw new NaoEncontrado("Visitante");
        reconhecimento.remover(id);
        alunos.remover(id);
    }

    /** Chamado a cada minuto. Se a biometria estiver fora, tenta de novo na próxima rodada. */
    public int expirar() {
        int removidos = 0;
        for (UUID id : visitantes.expiradosAte(relogio.agora())) {
            try {
                reconhecimento.remover(id);
                alunos.remover(id);
                removidos++;
            } catch (ReconhecimentoIndisponivel e) {
                // mantém aluno e biometria juntos; próxima rodada tenta de novo
            }
        }
        return removidos;
    }
}
```

O `VisitantesFake` precisa refletir a cascata do banco para `expiradosAte` não devolver ids já apagados: em `expirar`/`remover` o caso de uso só chama `alunos.remover`; no fake, `AlunosFake` não conhece visitantes. Ajustar o teste criando `f.alunos` com referência ao fake de visitantes **não** é necessário: `expiradosAte` devolver um id já removido leva a `reconhecimento.remover` (idempotente) e `alunos.remover` (idempotente), e `removidos++` contaria de novo. Para o fake ficar fiel, `VisitantesFake` recebe a remoção pela ligação abaixo em `Fakes`:

```java
    // em Fakes, depois dos campos:
    {
        alunos.aoRemover = visitantes.dados::remove;
    }
```

E em `AlunosFake`:

```java
        public java.util.function.Consumer<UUID> aoRemover = id -> {};
        public void remover(UUID id) { dados.remove(id); aoRemover.accept(id); }
```

(substitui o `remover` do Step 3).

- [ ] **Step 6: Rodar**

Run: `./mvnw -q test -Dtest='VisitantesTemporariosTest,RealizarCheckInTest,GestaoDeAlunosTest,CpfTest,ArquiteturaTest'`
Expected: PASS. (O compilador vai exigir `remover` em `AlunosJdbc`; adicionar provisoriamente `throw new UnsupportedOperationException()` — implementado de verdade na Task 2.)

- [ ] **Step 7: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): caso de uso do visitante temporário (Teste com você)"
```

---

### Task 2: Visitante na persistência, endpoints, expiração e aquecimento

**Files:**
- Create: `facegym-api/src/main/resources/db/migration/V2__visitantes.sql`
- Modify: `facegym-api/src/main/java/com/facegym/adapters/jdbc/AlunosJdbc.java`
- Create: `facegym-api/src/main/java/com/facegym/adapters/jdbc/VisitantesJdbc.java`
- Modify: `facegym-api/src/main/java/com/facegym/adapters/biometria/BiometriaHttpClient.java` (método `aquecer`)
- Create: `facegym-api/src/main/java/com/facegym/adapters/web/DemoController.java`
- Create: `facegym-api/src/main/java/com/facegym/adapters/config/ExpiracaoDeVisitantes.java`
- Modify: `UseCaseConfig.java`, `SecurityConfig.java`, `ErrosHandler.java`
- Test: `facegym-api/src/test/java/com/facegym/adapters/jdbc/JdbcAdaptersTest.java`, `facegym-api/src/test/java/com/facegym/FluxoCompletoIT.java`

**Interfaces:**
- Consumes: Task 1.
- Produces (contrato HTTP para o front):
  - `POST /api/v1/demo/visitantes` multipart `foto` + `consentimento=true` → 201 `{id, nome, cpf, expiraEm}`; 409 sem consentimento; 422 sem rosto; 429 limite; 503 biometria fora.
  - `DELETE /api/v1/demo/visitantes/{id}` → 204; 404 se não for visitante.
  - `POST /api/v1/demo/aquecer` → 202 (dispara `GET /health` da biometria em background, timeout 90 s).

- [ ] **Step 1: Migration**

```sql
-- facegym-api/src/main/resources/db/migration/V2__visitantes.sql
-- Apagar um aluno leva junto matrículas, acessos e o registro de visitante.
ALTER TABLE matricula DROP CONSTRAINT matricula_aluno_id_fkey,
    ADD CONSTRAINT matricula_aluno_id_fkey FOREIGN KEY (aluno_id) REFERENCES aluno(id) ON DELETE CASCADE;
ALTER TABLE acesso DROP CONSTRAINT acesso_aluno_id_fkey,
    ADD CONSTRAINT acesso_aluno_id_fkey FOREIGN KEY (aluno_id) REFERENCES aluno(id) ON DELETE CASCADE;

CREATE TABLE visitante (
    aluno_id   uuid PRIMARY KEY REFERENCES aluno(id) ON DELETE CASCADE,
    criado_em  timestamptz NOT NULL,
    expira_em  timestamptz NOT NULL
);
CREATE INDEX visitante_expira_idx ON visitante (expira_em);

-- Só instantes de criação, sem nenhum dado pessoal: sustenta o limite por hora
-- mesmo depois que o visitante é apagado.
CREATE TABLE visitante_log (criado_em timestamptz NOT NULL);
CREATE INDEX visitante_log_idx ON visitante_log (criado_em);

INSERT INTO plano (id, nome, preco, dias_semana, hora_inicio, hora_fim, acessos_semana)
VALUES ('00000000-0000-4000-8000-000000000001', 'Visitante', 0,
        'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY', '00:00', '23:59', NULL);
```

- [ ] **Step 2: Testes JDBC (falhando)** — adicionar em `JdbcAdaptersTest` (e `VisitantesJdbc.class` no `@Import`):

```java
    @Autowired VisitantesJdbc visitantes;

    @Test
    void removerAlunoApagaMatriculasAcessosEVisitanteEmCascata() {
        Aluno a = Aluno.novo("Visitante AB12", Cpf.of("16899535009"), null);
        alunos.salvar(a);
        matriculas.salvar(new Matricula(UUID.randomUUID(), a.id(), UUID.fromString("00000000-0000-4000-8000-000000000001"),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)));
        acessos.registrar(new Acesso(UUID.randomUUID(), Instant.now(), a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.FACIAL, 0.9));
        Instant t = Instant.parse("2026-10-05T11:00:00Z");
        visitantes.registrar(a.id(), t, t.plusSeconds(600));

        assertThat(visitantes.existe(a.id())).isTrue();
        assertThat(visitantes.expiradosAte(t.plusSeconds(599))).isEmpty();
        assertThat(visitantes.expiradosAte(t.plusSeconds(600))).containsExactly(a.id());

        alunos.remover(a.id());

        assertThat(alunos.porId(a.id())).isEmpty();
        assertThat(visitantes.existe(a.id())).isFalse();
        assertThat(visitantes.criadosDesde(t.minusSeconds(1))).isEqualTo(1); // log sobrevive
    }
```

Run: `./mvnw -q test -Dtest=JdbcAdaptersTest` → Expected: FAIL (compilação: `VisitantesJdbc`).

- [ ] **Step 3: Implementar adaptadores**

Em `AlunosJdbc`, substituir o `remover` provisório:

```java
    @Override
    public void remover(UUID id) {
        jdbc.sql("DELETE FROM aluno WHERE id = ?").param(id).update();
    }
```

```java
// facegym-api/src/main/java/com/facegym/adapters/jdbc/VisitantesJdbc.java
package com.facegym.adapters.jdbc;

import com.facegym.application.port.Visitantes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class VisitantesJdbc implements Visitantes {
    private final JdbcClient jdbc;

    public VisitantesJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void registrar(UUID alunoId, Instant criadoEm, Instant expiraEm) {
        jdbc.sql("INSERT INTO visitante (aluno_id, criado_em, expira_em) VALUES (?, ?, ?)")
                .param(alunoId).param(Timestamp.from(criadoEm)).param(Timestamp.from(expiraEm)).update();
        jdbc.sql("INSERT INTO visitante_log (criado_em) VALUES (?)").param(Timestamp.from(criadoEm)).update();
    }

    @Override
    public boolean existe(UUID alunoId) {
        return jdbc.sql("SELECT count(*) FROM visitante WHERE aluno_id = ?").param(alunoId).query(Long.class).single() > 0;
    }

    @Override
    public List<UUID> expiradosAte(Instant agora) {
        return jdbc.sql("SELECT aluno_id FROM visitante WHERE expira_em <= ?").param(Timestamp.from(agora))
                .query(UUID.class).list();
    }

    @Override
    public long criadosDesde(Instant desde) {
        return jdbc.sql("SELECT count(*) FROM visitante_log WHERE criado_em >= ?").param(Timestamp.from(desde))
                .query(Long.class).single();
    }
}
```

Run: `./mvnw -q test -Dtest=JdbcAdaptersTest` → Expected: PASS (5).

- [ ] **Step 4: Aquecimento da biometria** — adicionar em `BiometriaHttpClient` (fora da porta; só o adaptador web usa):

```java
    /** Acorda a biometria (Render free hiberna). Timeout longo, fora do circuito, sem bloquear quem chamou. */
    public void aquecer() {
        Thread.startVirtualThread(() -> {
            try {
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/health"))
                        .timeout(Duration.ofSeconds(90)).GET().build();
                java.net.http.HttpClient.newHttpClient().send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // melhor esforço
            }
        });
    }
```

E guardar a URL no construtor: campo `private final String baseUrl;` com `this.baseUrl = props.url();`.

- [ ] **Step 5: Controller, expiração agendada, segurança e erros**

```java
// facegym-api/src/main/java/com/facegym/adapters/web/DemoController.java
package com.facegym.adapters.web;

import com.facegym.adapters.biometria.BiometriaHttpClient;
import com.facegym.application.VisitantesTemporarios;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final VisitantesTemporarios visitantes;
    private final BiometriaHttpClient biometria;

    public DemoController(VisitantesTemporarios visitantes, BiometriaHttpClient biometria) {
        this.visitantes = visitantes;
        this.biometria = biometria;
    }

    @PostMapping("/visitantes")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitantesTemporarios.Visitante criar(@RequestParam("foto") MultipartFile foto,
                                                 @RequestParam(defaultValue = "false") boolean consentimento) throws IOException {
        return visitantes.criar(foto.getBytes(), consentimento);
    }

    @DeleteMapping("/visitantes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) { visitantes.remover(id); }

    @PostMapping("/aquecer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void aquecer() { biometria.aquecer(); }
}
```

```java
// facegym-api/src/main/java/com/facegym/adapters/config/ExpiracaoDeVisitantes.java
package com.facegym.adapters.config;

import com.facegym.application.VisitantesTemporarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ExpiracaoDeVisitantes {
    private static final Logger log = LoggerFactory.getLogger(ExpiracaoDeVisitantes.class);
    private final VisitantesTemporarios visitantes;

    public ExpiracaoDeVisitantes(VisitantesTemporarios visitantes) { this.visitantes = visitantes; }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirar() {
        int n = visitantes.expirar();
        if (n > 0) log.info("{} visitante(s) temporário(s) apagado(s)", n);
    }
}
```

Em `UseCaseConfig`, adicionar:

```java
    @Bean
    VisitantesTemporarios visitantesTemporarios(Alunos a, Matriculas m, Visitantes v, ReconhecimentoFacial r, Relogio rel) {
        return new VisitantesTemporarios(a, m, v, r, rel);
    }
```

Em `SecurityConfig`, trocar o bloco de `permitAll` do POST por:

```java
                        .requestMatchers(HttpMethod.POST, "/api/v1/check-ins/**", "/api/v1/demo/identificar",
                                "/api/v1/demo/visitantes", "/api/v1/demo/aquecer", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/demo/visitantes/*").permitAll()
```

Em `ErrosHandler`, adicionar:

```java
    @ExceptionHandler(com.facegym.application.LimiteDeVisitantes.class)
    ResponseEntity<Map<String, String>> limite(com.facegym.application.LimiteDeVisitantes e) {
        return erro(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
```

- [ ] **Step 6: Teste de ponta a ponta do visitante (falhando antes dos Steps 3–5)** — adicionar em `FluxoCompletoIT`:

```java
    @Test
    void visitanteSeCadastraFazCheckInEApagaOsDados() throws Exception {
        var selfie = new MockMultipartFile("foto", "s.jpg", "image/jpeg", new byte[]{5});
        bio.stubFor(WireMock.put(WireMock.urlMatching("/faces/.*")).willReturn(WireMock.noContent()));
        String body = mvc.perform(multipart("/api/v1/demo/visitantes").file(selfie).param("consentimento", "true"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = id(body);

        bio.stubFor(WireMock.post("/faces/identify").willReturn(WireMock.okJson("{\"alunoId\":\"" + id + "\",\"score\":0.9}")));
        mvc.perform(multipart("/api/v1/check-ins").file(selfie))
                .andExpect(jsonPath("$.status").value("LIBERADO"))
                .andExpect(jsonPath("$.nome").value(org.hamcrest.Matchers.startsWith("Visitante ")));

        bio.stubFor(WireMock.delete(WireMock.urlMatching("/faces/.*")).willReturn(WireMock.noContent()));
        mvc.perform(delete("/api/v1/demo/visitantes/" + id)).andExpect(status().isNoContent());
        mvc.perform(multipart("/api/v1/check-ins").file(selfie)).andExpect(jsonPath("$.status").value("NAO_RECONHECIDO"));
    }

    @Test
    void visitanteSemConsentimentoE409() throws Exception {
        mvc.perform(multipart("/api/v1/demo/visitantes").file(new MockMultipartFile("foto", "s.jpg", "image/jpeg", new byte[]{5})))
                .andExpect(status().isConflict());
    }
```

- [ ] **Step 7: Rodar tudo**

Run: `./mvnw -q verify` → Expected: todos passam (58 + 1 CPF + 7 visitante + 1 JDBC + 2 IT).

- [ ] **Step 8: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): endpoints do visitante temporário, expiração agendada e aquecimento da biometria"
```

---

### Task 3: Rostos da demo (Canva), variações e script de seed

**Files:**
- Create: `scripts/demo-fotos/{ana,bruno,carla,diego,elisa}.jpg` (cadastro)
- Create: `facegym-web/public/demo/{ana,bruno,carla,diego,elisa}.jpg` (check-in: variação)
- Create: `scripts/variacoes.py`, `scripts/seed-demo.sh`, `scripts/demo-fotos/LICENSE.md`

**Interfaces:**
- Consumes: API admin (plano 2 Task 7).
- Produces: `facegym-web/public/demo/alunos.json` — `[{ "slug": "ana", "nome": "Ana Souza", "cenario": "Plano em dia" }, ...]`, lido pelo totem (Task 4).

Cenários (um por aluno): Ana — em dia (plano Livre); Bruno — plano vencido ontem; Carla — plano "Madrugada" 03:00–04:00 (fora do horário quase sempre); Diego — "3x por semana" com 3 acessos já usados; Elisa — em dia, mas bloqueada ("Falta de atestado médico").

- [ ] **Step 1: Gerar os 5 rostos no Canva**

Para cada aluno, chamar a ferramenta `generate-image` do Canva com `aspectRatio: SQUARE_1_1` e o prompt:

> Photorealistic passport-style headshot of a fictional Brazilian {descrição}, facing the camera, neutral expression, even soft lighting, plain light gray background, shoulders visible, sharp focus, no text, no accessories covering the face

Descrições: ana = "woman in her late 20s" (já gerada: media `MAHWgdMPqL8`); bruno = "man in his 40s with short gray hair"; carla = "woman in her 50s with curly hair"; diego = "man in his early 20s with a short beard"; elisa = "woman in her 30s with straight black hair".

Depois de cada `SUCCESS`, chamar `get-assets` com o media id e baixar `thumbnail.url` para `scripts/demo-fotos/<slug>.jpg` (`curl -s -o ...`).

- [ ] **Step 2: Checar detecção de cada foto (teste)**

```bash
cd facegym-biometria && .venv/bin/python - <<'EOF'
from app.images import decode_image
from app.embedder import InsightFaceEmbedder
e = InsightFaceEmbedder()
for s in ["ana", "bruno", "carla", "diego", "elisa"]:
    v = e.embed(decode_image(open(f"../scripts/demo-fotos/{s}.jpg", "rb").read()))
    print(s, "OK" if v is not None else "SEM ROSTO")
EOF
```

Expected: 5 × `OK`. Se alguma der `SEM ROSTO`, gerar de novo aquele rosto.

- [ ] **Step 3: Variações para o check-in**

```python
# scripts/variacoes.py
"""Gera a foto de check-in de cada aluno da demo: recorte, espelho e brilho.
Assim o score fica realista (~0.9) em vez de 1.0 da mesma imagem."""
import sys
from pathlib import Path
from PIL import Image, ImageEnhance, ImageOps

origem, destino = Path(sys.argv[1]), Path(sys.argv[2])
destino.mkdir(parents=True, exist_ok=True)
for foto in sorted(origem.glob("*.jpg")):
    img = Image.open(foto).convert("RGB")
    w, h = img.size
    img = img.crop((int(w * 0.05), int(h * 0.03), int(w * 0.97), int(h * 0.98)))
    img = ImageEnhance.Brightness(ImageOps.mirror(img)).enhance(1.15)
    img.save(destino / foto.name, quality=90)
    print("variação:", destino / foto.name)
```

Run: `facegym-biometria/.venv/bin/python scripts/variacoes.py scripts/demo-fotos facegym-web/public/demo`

Checar o score de cada par (mesma pessoa ≥ 0.41 e pares diferentes < 0.18):

```bash
cd facegym-biometria && .venv/bin/python - <<'EOF'
import itertools
from app.images import decode_image
from app.embedder import InsightFaceEmbedder
e = InsightFaceEmbedder()
S = ["ana", "bruno", "carla", "diego", "elisa"]
cad = {s: e.embed(decode_image(open(f"../scripts/demo-fotos/{s}.jpg", "rb").read())) for s in S}
chk = {s: e.embed(decode_image(open(f"../facegym-web/public/demo/{s}.jpg", "rb").read())) for s in S}
for s in S: print(s, "mesma pessoa:", round(float(cad[s] @ chk[s]), 3))
print("pior par diferente:", round(max(float(cad[a] @ chk[b]) for a, b in itertools.permutations(S, 2)), 3))
EOF
```

Expected: todos "mesma pessoa" ≥ 0.41; "pior par diferente" < 0.18. Se um par diferente passar de 0.18, regerar um dos rostos.

- [ ] **Step 4: Metadados e licença**

```json
// facegym-web/public/demo/alunos.json
[
  { "slug": "ana", "nome": "Ana Souza", "cenario": "Plano em dia" },
  { "slug": "bruno", "nome": "Bruno Lima", "cenario": "Plano vencido" },
  { "slug": "carla", "nome": "Carla Mendes", "cenario": "Fora do horário do plano" },
  { "slug": "diego", "nome": "Diego Rocha", "cenario": "Limite semanal atingido" },
  { "slug": "elisa", "nome": "Elisa Prado", "cenario": "Aluna bloqueada" }
]
```

(Arquivo JSON puro: sem a linha de comentário.)

```markdown
<!-- scripts/demo-fotos/LICENSE.md -->
# Fotos da demo

Rostos **fictícios gerados por IA** no Canva (ferramenta de geração de imagens da conta do autor),
em 2026-09-28. Não retratam pessoas reais. As fotos de check-in em `facegym-web/public/demo/` são
variações (recorte, espelho e brilho) geradas por `scripts/variacoes.py`.
```

- [ ] **Step 5: Script de seed**

```bash
#!/usr/bin/env bash
# scripts/seed-demo.sh — popula a API com os 5 alunos da demo. Idempotente.
# Uso: API=https://.../api/v1 ADMIN_EMAIL=... ADMIN_PASSWORD=... scripts/seed-demo.sh
set -euo pipefail
API=${API:-http://localhost:8081/api/v1}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@facegym.dev}
: "${ADMIN_PASSWORD:?defina ADMIN_PASSWORD}"
DIR=$(cd "$(dirname "$0")" && pwd)

j() { curl -sS -H 'Content-Type: application/json' "$@"; }
T=$(j -X POST "$API/auth/login" -d "{\"email\":\"$ADMIN_EMAIL\",\"senha\":\"$ADMIN_PASSWORD\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/')
A="Authorization: Bearer $T"
id() { sed -E 's/.*"id":"([^"]+)".*/\1/'; }

plano() { # nome, dias, inicio, fim, limite
  local existente
  existente=$(curl -sS -H "$A" "$API/planos" | python3 -c "import sys,json; print(next((p['id'] for p in json.load(sys.stdin) if p['nome']=='$1'), ''))")
  if [ -n "$existente" ]; then echo "$existente"; return; fi
  j -H "$A" -X POST "$API/planos" -d "{\"nome\":\"$1\",\"preco\":99.9,\"dias\":$2,\"inicio\":\"$3\",\"fim\":\"$4\",\"acessosPorSemana\":$5}" | id
}
TODOS='["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]'
LIVRE=$(plano "Livre" "$TODOS" "00:00" "23:59" null)
MADRUGADA=$(plano "Madrugada" "$TODOS" "03:00" "04:00" null)
TRES=$(plano "3x por semana" "$TODOS" "00:00" "23:59" 3)

HOJE=$(date +%F)
ONTEM=$(date -v-1d +%F 2>/dev/null || date -d yesterday +%F)
INICIO=$(date -v-60d +%F 2>/dev/null || date -d '60 days ago' +%F)
FIM=$(date -v+365d +%F 2>/dev/null || date -d '365 days' +%F)

aluno() { # slug, nome, cpf, plano, vencimento
  local existente
  existente=$(curl -sS -H "$A" "$API/alunos" | python3 -c "import sys,json; print(next((a['id'] for a in json.load(sys.stdin) if a['cpf']=='$3'), ''))")
  if [ -n "$existente" ]; then echo "$existente"; return; fi
  local ID
  ID=$(j -H "$A" -X POST "$API/alunos" -d "{\"nome\":\"$2\",\"cpf\":\"$3\"}" | id)
  j -H "$A" -X POST "$API/matriculas" -d "{\"alunoId\":\"$ID\",\"planoId\":\"$4\",\"inicio\":\"$INICIO\",\"vencimento\":\"$5\"}" >/dev/null
  curl -sS -H "$A" -X POST "$API/alunos/$ID/consentimento" >/dev/null
  curl -sS -f -H "$A" -X PUT "$API/alunos/$ID/biometria" -F foto=@"$DIR/demo-fotos/$1.jpg" >/dev/null
  echo "$ID"
}

aluno ana   "Ana Souza"    "52998224725" "$LIVRE"     "$FIM"   >/dev/null
aluno bruno "Bruno Lima"   "11144477735" "$LIVRE"     "$ONTEM" >/dev/null
aluno carla "Carla Mendes" "39053344705" "$MADRUGADA" "$FIM"   >/dev/null
aluno diego "Diego Rocha"  "86288366757" "$TRES"      "$FIM"   >/dev/null
ELISA=$(aluno elisa "Elisa Prado" "34608514300" "$LIVRE" "$FIM")
j -H "$A" -X POST "$API/alunos/$ELISA/bloqueio" -d '{"motivo":"Falta de atestado médico"}' -o /dev/null -w ''

# Diego: completa 3 acessos na semana (roda de novo na segunda para manter o cenário)
for _ in 1 2 3; do
  R=$(j -X POST "$API/check-ins/cpf" -d '{"cpf":"86288366757"}')
  echo "$R" | grep -q NEGADO && break
done
echo "Seed concluído."
```

- [ ] **Step 6: Rodar contra o compose local e checar cada cenário**

```bash
docker compose up -d --build
until curl -sf localhost:8081/actuator/health >/dev/null; do sleep 3; done
chmod +x scripts/seed-demo.sh && ADMIN_PASSWORD=admin12345 scripts/seed-demo.sh
for s in ana bruno carla diego elisa; do
  printf "%s: " $s; curl -s -X POST localhost:8081/api/v1/check-ins -F foto=@facegym-web/public/demo/$s.jpg; echo
done
ADMIN_PASSWORD=admin12345 scripts/seed-demo.sh   # segunda vez: não duplica
docker compose down
```

Expected: Ana `LIBERADO`; Bruno `NEGADO` "Plano vencido ou inexistente"; Carla `NEGADO` "Plano Madrugada: fora do horário (03:00–04:00)" (fora das 3h); Diego `NEGADO` "limite de 3 acessos"; Elisa `NEGADO` "Aluno bloqueado: Falta de atestado médico". Segunda execução do seed sem erro.

- [ ] **Step 7: Commit**

```bash
git add scripts facegym-web/public/demo
git commit -m "feat(demo): rostos fictícios gerados no Canva, variações e seed dos 5 cenários"
```

---

### Task 4: Front — projeto, cliente da API e totem com galeria

**Files:**
- Create: `facegym-web/` (Vite react-ts), `facegym-web/src/{api.ts,resultado.ts,resultado.test.ts,Totem.tsx,ResultadoCard.tsx,App.tsx,main.tsx,index.css}`, `facegym-web/vercel.json`, `facegym-web/.env.example`

**Interfaces:**
- Consumes: contrato HTTP (plano 2 Task 7; Task 2 deste plano).
- Produces:
  - `api.ts`: `API_URL`, `class ApiError(status, mensagem)`, `apiJson<T>(path, init?)`, `apiForm<T>(path, form, method='POST')`, token do painel em `sessionStorage` (`getToken/setToken`).
  - `resultado.ts`: `type RespostaCheckIn = { status: 'LIBERADO'|'NEGADO'|'CONFIRMAR_CPF'|'NAO_RECONHECIDO'|'BIOMETRIA_INDISPONIVEL'; nome?: string; motivo?: string; token?: string }`; `function descrever(r): { titulo: string; detalhe?: string; tom: 'ok'|'erro'|'aviso'; pedeCpf: boolean }`.
  - `ResultadoCard({ resposta, onCpf })` — mostra o resultado e, se `pedeCpf`, um campo de CPF.

- [ ] **Step 1: Scaffold**

```bash
npm create vite@latest facegym-web -- --template react-ts
cd facegym-web && npm i && npm i react-router-dom && npm i -D tailwindcss @tailwindcss/vite vitest
rm -f src/App.css && rm -rf src/assets
```

`vite.config.ts`:

```ts
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({ plugins: [react(), tailwindcss()] })
```

`src/index.css`:

```css
@import "tailwindcss";
body { @apply bg-slate-950 text-slate-100 antialiased; }
```

`vercel.json`: `{ "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }`
`.env.example`: `VITE_API_URL=http://localhost:8081/api/v1`
`package.json` scripts: adicionar `"test": "vitest run"`.

- [ ] **Step 2: Teste do mapeamento de resultado (falhando)**

```ts
// facegym-web/src/resultado.test.ts
import { describe, expect, it } from 'vitest'
import { descrever } from './resultado'

describe('descrever', () => {
  it('liberado mostra boas-vindas', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' })).toEqual({ titulo: 'Bem-vinda(o), Ana!', tom: 'ok', pedeCpf: false })
  })
  it('negado mostra o motivo', () => {
    expect(descrever({ status: 'NEGADO', nome: 'Bruno', motivo: 'Plano vencido ou inexistente' }))
      .toEqual({ titulo: 'Acesso negado, Bruno', detalhe: 'Plano vencido ou inexistente', tom: 'erro', pedeCpf: false })
  })
  it('dúvida, não reconhecido e biometria fora pedem CPF', () => {
    for (const status of ['CONFIRMAR_CPF', 'NAO_RECONHECIDO', 'BIOMETRIA_INDISPONIVEL'] as const) {
      expect(descrever({ status }).pedeCpf).toBe(true)
    }
    expect(descrever({ status: 'BIOMETRIA_INDISPONIVEL' }).titulo).toBe('Reconhecimento indisponível')
  })
})
```

Run: `npm test` → Expected: FAIL (`Cannot find module './resultado'`).

- [ ] **Step 3: Implementar resultado e cliente**

```ts
// facegym-web/src/resultado.ts
export type RespostaCheckIn = {
  status: 'LIBERADO' | 'NEGADO' | 'CONFIRMAR_CPF' | 'NAO_RECONHECIDO' | 'BIOMETRIA_INDISPONIVEL'
  nome?: string
  motivo?: string
  token?: string
}

export type Descricao = { titulo: string; detalhe?: string; tom: 'ok' | 'erro' | 'aviso'; pedeCpf: boolean }

export function descrever(r: RespostaCheckIn): Descricao {
  switch (r.status) {
    case 'LIBERADO':
      return { titulo: `Bem-vinda(o), ${r.nome}!`, tom: 'ok', pedeCpf: false }
    case 'NEGADO':
      return { titulo: `Acesso negado, ${r.nome}`, detalhe: r.motivo, tom: 'erro', pedeCpf: false }
    case 'CONFIRMAR_CPF':
      return { titulo: 'Quase lá', detalhe: 'Confirme seu CPF para entrar.', tom: 'aviso', pedeCpf: true }
    case 'NAO_RECONHECIDO':
      return { titulo: 'Rosto não reconhecido', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'BIOMETRIA_INDISPONIVEL':
      return { titulo: 'Reconhecimento indisponível', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
  }
}
```

```ts
// facegym-web/src/api.ts
export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api/v1'
const TOKEN_KEY = 'facegym.token'

export class ApiError extends Error {
  status: number
  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.status = status
  }
}

export function getToken(): string | null {
  try { return sessionStorage.getItem(TOKEN_KEY) } catch { return null }
}
export function setToken(t: string | null) {
  try { if (t) sessionStorage.setItem(TOKEN_KEY, t); else sessionStorage.removeItem(TOKEN_KEY) } catch { /* sem storage */ }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token && path.startsWith('/') && !path.startsWith('/check-ins') && !path.startsWith('/demo')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  let res: Response
  try {
    res = await fetch(`${API_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Não foi possível falar com o servidor. Ele pode estar acordando; tente de novo em alguns segundos.')
  }
  if (res.status === 401 && token) setToken(null)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.mensagem ?? `Erro ${res.status}`)
  }
  if (res.status === 204 || res.status === 202) return undefined as T
  return (await res.json()) as T
}

export const apiJson = <T>(path: string, init: RequestInit = {}) =>
  request<T>(path, { ...init, headers: { 'Content-Type': 'application/json', ...init.headers } })

export const apiForm = <T>(path: string, form: FormData, method = 'POST') =>
  request<T>(path, { method, body: form })

export async function fotoDeUrl(url: string): Promise<Blob> {
  return (await fetch(url)).blob()
}
```

Run: `npm test` → Expected: PASS (3).

- [ ] **Step 4: Cartão de resultado e totem com galeria**

```tsx
// facegym-web/src/ResultadoCard.tsx
import { useState, type FormEvent } from 'react'
import { descrever, type RespostaCheckIn } from './resultado'

const TONS = {
  ok: 'border-emerald-500 bg-emerald-500/10 text-emerald-300',
  erro: 'border-rose-500 bg-rose-500/10 text-rose-300',
  aviso: 'border-amber-500 bg-amber-500/10 text-amber-200',
}

export function ResultadoCard({ resposta, onCpf, ocupado }: {
  resposta: RespostaCheckIn
  onCpf: (cpf: string) => void
  ocupado: boolean
}) {
  const d = descrever(resposta)
  const [cpf, setCpf] = useState('')
  const enviar = (e: FormEvent) => { e.preventDefault(); onCpf(cpf) }
  return (
    <div className={`rounded-2xl border-2 p-6 ${TONS[d.tom]}`} role="status" aria-live="polite">
      <p className="text-2xl font-semibold">{d.titulo}</p>
      {d.detalhe && <p className="mt-1 text-lg">{d.detalhe}</p>}
      {d.pedeCpf && (
        <form onSubmit={enviar} className="mt-4 flex gap-2">
          <input value={cpf} onChange={(e) => setCpf(e.target.value)} inputMode="numeric" placeholder="CPF"
            className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-slate-100 outline-none ring-1 ring-slate-700 focus:ring-emerald-500" />
          <button disabled={ocupado || !cpf} className="rounded-lg bg-emerald-500 px-4 py-2 font-medium text-slate-950 disabled:opacity-50">Entrar</button>
        </form>
      )}
    </div>
  )
}
```

```tsx
// facegym-web/src/Totem.tsx
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiForm, apiJson, fotoDeUrl } from './api'
import { ResultadoCard } from './ResultadoCard'
import type { RespostaCheckIn } from './resultado'
import { TesteComVoce } from './TesteComVoce'

type AlunoDemo = { slug: string; nome: string; cenario: string }

export function Totem() {
  const [alunos, setAlunos] = useState<AlunoDemo[]>([])
  const [resposta, setResposta] = useState<RespostaCheckIn | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    fetch('/demo/alunos.json').then((r) => r.json()).then(setAlunos).catch(() => setAlunos([]))
    apiJson('/demo/aquecer', { method: 'POST' }).catch(() => {}) // acorda a biometria no Render free
  }, [])

  async function executar(acao: () => Promise<RespostaCheckIn>) {
    setOcupado(true); setErro(null)
    try { setResposta(await acao()) }
    catch (e) { setErro(e instanceof ApiError ? e.message : 'Erro inesperado') }
    finally { setOcupado(false) }
  }

  const checkInFoto = (foto: Blob) => executar(() => {
    const form = new FormData(); form.append('foto', foto, 'foto.jpg')
    return apiForm<RespostaCheckIn>('/check-ins', form)
  })

  const checkInCpf = (cpf: string) => executar(() =>
    resposta?.status === 'CONFIRMAR_CPF' && resposta.token
      ? apiJson<RespostaCheckIn>(`/check-ins/${resposta.token}/cpf`, { method: 'POST', body: JSON.stringify({ cpf }) })
      : apiJson<RespostaCheckIn>('/check-ins/cpf', { method: 'POST', body: JSON.stringify({ cpf }) }))

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 flex items-center justify-between">
        <h1 className="text-2xl font-bold">FaceGym <span className="text-emerald-400">· totem</span></h1>
        <Link to="/painel" className="text-sm text-slate-400 hover:text-slate-100">Painel da academia →</Link>
      </header>

      <section aria-labelledby="galeria">
        <h2 id="galeria" className="mb-1 text-lg font-semibold">Alunos de demonstração</h2>
        <p className="mb-4 text-sm text-slate-400">Rostos fictícios gerados por IA. Clique em um para fazer o check-in com uma foto diferente da cadastrada.</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {alunos.map((a) => (
            <button key={a.slug} disabled={ocupado} onClick={async () => checkInFoto(await fotoDeUrl(`/demo/${a.slug}.jpg`))}
              className="overflow-hidden rounded-xl bg-slate-900 text-left ring-1 ring-slate-800 transition hover:ring-emerald-500 disabled:opacity-50">
              <img src={`/demo/${a.slug}.jpg`} alt={`Foto de ${a.nome}`} className="aspect-square w-full object-cover" />
              <div className="p-2">
                <p className="text-sm font-medium">{a.nome}</p>
                <p className="text-xs text-slate-400">{a.cenario}</p>
              </div>
            </button>
          ))}
        </div>
      </section>

      <section className="mt-6 min-h-24">
        {ocupado && <p className="text-slate-400">Reconhecendo… (na primeira vez o servidor pode levar até 1 minuto para acordar)</p>}
        {erro && <p className="rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}
        {resposta && !ocupado && <ResultadoCard resposta={resposta} onCpf={checkInCpf} ocupado={ocupado} />}
      </section>

      <TesteComVoce onCheckIn={checkInFoto} ocupado={ocupado} />
    </main>
  )
}
```

`TesteComVoce` é criado na Task 5; nesta task, criar o arquivo mínimo para compilar:

```tsx
// facegym-web/src/TesteComVoce.tsx
export function TesteComVoce(_: { onCheckIn: (foto: Blob) => void; ocupado: boolean }) {
  return null
}
```

```tsx
// facegym-web/src/App.tsx
import { Route, Routes } from 'react-router-dom'
import { Totem } from './Totem'

export default function App() {
  return (
    <Routes>
      <Route path="*" element={<Totem />} />
    </Routes>
  )
}
```

```tsx
// facegym-web/src/main.tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter><App /></BrowserRouter>
  </StrictMode>,
)
```

Em `index.html`: `<html lang="pt-BR">` e `<title>FaceGym</title>`.

- [ ] **Step 5: Build e verificação no navegador**

Run: `npm run build && npm test` → Expected: build sem erros; 3 testes passam.

Com o compose e o seed da Task 3 no ar (`docker compose up -d`, API em 8081; CORS: adicionar `CORS_ALLOWED_ORIGINS: http://localhost:5173` no serviço `api` do `docker-compose.yml`), rodar `npm run dev`, abrir `http://localhost:5173` no navegador embutido, clicar em cada um dos 5 alunos e conferir os 5 resultados esperados da Task 3 Step 6. Clicar em Bruno e entrar com o CPF `111.444.777-35` no cartão → continua `NEGADO` pelo plano vencido.

- [ ] **Step 6: Commit**

```bash
git add facegym-web docker-compose.yml
git commit -m "feat(web): totem com galeria de demo, resultado e CPF"
```

---

### Task 5: "Teste com você" (câmera) e painel da academia

**Files:**
- Create: `facegym-web/src/Camera.tsx`
- Modify: `facegym-web/src/TesteComVoce.tsx`
- Create: `facegym-web/src/painel/{Login,Painel}.tsx`
- Modify: `facegym-web/src/App.tsx`

**Interfaces:**
- Consumes: `api.ts` (Task 4); `POST/DELETE /demo/visitantes` (Task 2); rotas admin (plano 2).
- Produces: `Camera({ onFoto, rotulo })` — pede permissão, mostra o vídeo e devolve um `Blob` JPEG; em erro de permissão, mostra mensagem.

- [ ] **Step 1: Câmera**

```tsx
// facegym-web/src/Camera.tsx
import { useEffect, useRef, useState } from 'react'

export function Camera({ onFoto, rotulo, desabilitado }: { onFoto: (foto: Blob) => void; rotulo: string; desabilitado?: boolean }) {
  const video = useRef<HTMLVideoElement>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'user', width: 640, height: 640 } })
      .then((s) => { stream = s; if (video.current) video.current.srcObject = s })
      .catch(() => setErro('Não consegui acessar a câmera. Verifique a permissão do navegador ou use a galeria acima.'))
    if (!navigator.mediaDevices) setErro('Este navegador não oferece câmera. Use a galeria acima.')
    return () => stream?.getTracks().forEach((t) => t.stop())
  }, [])

  function capturar() {
    const v = video.current
    if (!v || !v.videoWidth) return
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth; canvas.height = v.videoHeight
    canvas.getContext('2d')!.drawImage(v, 0, 0)
    canvas.toBlob((b) => b && onFoto(b), 'image/jpeg', 0.9)
  }

  if (erro) return <p className="rounded-lg bg-amber-500/10 p-3 text-amber-200">{erro}</p>
  return (
    <div className="flex flex-col items-center gap-3">
      <video ref={video} autoPlay playsInline muted className="aspect-square w-64 rounded-2xl bg-black object-cover [transform:scaleX(-1)]" />
      <button onClick={capturar} disabled={desabilitado}
        className="rounded-lg bg-emerald-500 px-4 py-2 font-medium text-slate-950 disabled:opacity-50">{rotulo}</button>
    </div>
  )
}
```

- [ ] **Step 2: Fluxo "Teste com você"**

```tsx
// facegym-web/src/TesteComVoce.tsx
import { useEffect, useState } from 'react'
import { ApiError, apiForm, apiJson } from './api'
import { Camera } from './Camera'

type Visitante = { id: string; nome: string; cpf: string; expiraEm: string }

export function TesteComVoce({ onCheckIn, ocupado }: { onCheckIn: (foto: Blob) => void; ocupado: boolean }) {
  const [aberto, setAberto] = useState(false)
  const [consentiu, setConsentiu] = useState(false)
  const [visitante, setVisitante] = useState<Visitante | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [agora, setAgora] = useState(Date.now())

  useEffect(() => {
    if (!visitante) return
    const t = setInterval(() => setAgora(Date.now()), 1000)
    return () => clearInterval(t)
  }, [visitante])

  const restante = visitante ? Math.max(0, new Date(visitante.expiraEm).getTime() - agora) : 0
  useEffect(() => { if (visitante && restante === 0) setVisitante(null) }, [visitante, restante])

  async function cadastrar(foto: Blob) {
    setCarregando(true); setErro(null)
    try {
      const form = new FormData()
      form.append('foto', foto, 'selfie.jpg')
      form.append('consentimento', 'true')
      setVisitante(await apiForm<Visitante>('/demo/visitantes', form))
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Erro inesperado')
    } finally { setCarregando(false) }
  }

  async function apagar() {
    if (!visitante) return
    await apiJson(`/demo/visitantes/${visitante.id}`, { method: 'DELETE' }).catch(() => {})
    setVisitante(null); setConsentiu(false)
  }

  const mmss = `${Math.floor(restante / 60000)}:${String(Math.floor(restante / 1000) % 60).padStart(2, '0')}`

  return (
    <section className="mt-10 rounded-2xl bg-slate-900 p-6 ring-1 ring-slate-800">
      <h2 className="text-lg font-semibold">Teste com você</h2>
      <p className="mt-1 text-sm text-slate-400">
        Cadastre seu rosto por 10 minutos e faça o check-in de verdade. Guardamos só um vetor numérico do rosto,
        nunca a foto, e tudo é apagado automaticamente.
      </p>

      {!aberto && <button onClick={() => setAberto(true)} className="mt-4 rounded-lg bg-slate-100 px-4 py-2 font-medium text-slate-950">Quero testar</button>}

      {aberto && !visitante && (
        <div className="mt-4 space-y-4">
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={consentiu} onChange={(e) => setConsentiu(e.target.checked)} className="mt-1" />
            <span>Autorizo o uso da minha biometria facial só para este teste, com exclusão automática em 10 minutos.</span>
          </label>
          {consentiu && <Camera rotulo={carregando ? 'Cadastrando…' : '1. Cadastrar meu rosto'} onFoto={cadastrar} desabilitado={carregando} />}
          {erro && <p className="rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}
        </div>
      )}

      {visitante && (
        <div className="mt-4 space-y-4">
          <p className="text-sm">
            Você é <strong>{visitante.nome}</strong> (CPF fictício {visitante.cpf}). Seus dados somem em <strong>{mmss}</strong>.
          </p>
          <Camera rotulo="2. Fazer check-in" onFoto={onCheckIn} desabilitado={ocupado} />
          <button onClick={apagar} className="text-sm text-rose-300 underline">Apagar meus dados agora</button>
        </div>
      )}
    </section>
  )
}
```

- [ ] **Step 3: Painel**

```tsx
// facegym-web/src/painel/Login.tsx
import { useState, type FormEvent } from 'react'
import { ApiError, apiJson, setToken } from '../api'

export function Login({ onEntrar }: { onEntrar: () => void }) {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)

  async function entrar(e: FormEvent) {
    e.preventDefault(); setErro(null)
    try {
      const { token } = await apiJson<{ token: string }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, senha }) })
      setToken(token); onEntrar()
    } catch (e) { setErro(e instanceof ApiError ? e.message : 'Erro inesperado') }
  }

  return (
    <form onSubmit={entrar} className="mx-auto mt-16 max-w-sm space-y-3 rounded-2xl bg-slate-900 p-6 ring-1 ring-slate-800">
      <h1 className="text-xl font-semibold">Painel da academia</h1>
      <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="E-mail" required autoComplete="username"
        className="w-full rounded-lg bg-slate-950 px-3 py-2 ring-1 ring-slate-700" />
      <input value={senha} onChange={(e) => setSenha(e.target.value)} type="password" placeholder="Senha" required autoComplete="current-password"
        className="w-full rounded-lg bg-slate-950 px-3 py-2 ring-1 ring-slate-700" />
      {erro && <p className="text-sm text-rose-300">{erro}</p>}
      <button className="w-full rounded-lg bg-emerald-500 py-2 font-medium text-slate-950">Entrar</button>
    </form>
  )
}
```

```tsx
// facegym-web/src/painel/Painel.tsx
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiJson, getToken, setToken } from '../api'
import { Login } from './Login'

type Aluno = { id: string; nome: string; cpf: string; bloqueado: boolean; motivoBloqueio: string | null; consentimentoBiometricoEm: string | null }
type Plano = { id: string; nome: string; inicio: string; fim: string; dias: string[]; acessosPorSemana: number | null }
type Acesso = { id: string; dataHora: string; alunoId: string | null; resultado: 'LIBERADO' | 'NEGADO'; motivo: string | null; meio: 'FACIAL' | 'CPF'; score: number | null }

export function Painel() {
  const [logado, setLogado] = useState(!!getToken())
  const [aba, setAba] = useState<'acessos' | 'alunos' | 'planos'>('acessos')
  const [alunos, setAlunos] = useState<Aluno[]>([])
  const [planos, setPlanos] = useState<Plano[]>([])
  const [acessos, setAcessos] = useState<Acesso[]>([])
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    try {
      const [al, pl, ac] = await Promise.all([
        apiJson<Aluno[]>('/alunos'), apiJson<Plano[]>('/planos'), apiJson<Acesso[]>('/acessos?limite=50')])
      setAlunos(al); setPlanos(pl); setAcessos(ac); setErro(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setLogado(false)
      else setErro(e instanceof ApiError ? e.message : 'Erro inesperado')
    }
  }, [])

  useEffect(() => {
    if (!logado) return
    carregar()
    const t = setInterval(carregar, 5000)
    return () => clearInterval(t)
  }, [logado, carregar])

  if (!logado) return <Login onEntrar={() => setLogado(true)} />

  const nomeDe = (id: string | null) => alunos.find((a) => a.id === id)?.nome ?? '—'
  const acao = (fn: () => Promise<unknown>) => fn().then(carregar).catch((e) => setErro(e instanceof ApiError ? e.message : 'Erro'))

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Painel <span className="text-emerald-400">FaceGym</span></h1>
        <div className="flex gap-4 text-sm">
          <Link to="/" className="text-slate-400 hover:text-slate-100">← Totem</Link>
          <button onClick={() => { setToken(null); setLogado(false) }} className="text-slate-400 hover:text-rose-300">Sair</button>
        </div>
      </header>
      <nav className="mb-4 flex gap-2">
        {(['acessos', 'alunos', 'planos'] as const).map((a) => (
          <button key={a} onClick={() => setAba(a)}
            className={`rounded-lg px-3 py-1.5 text-sm capitalize ${aba === a ? 'bg-emerald-500 text-slate-950' : 'bg-slate-900 text-slate-300'}`}>{a}</button>
        ))}
      </nav>
      {erro && <p className="mb-4 rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}

      {aba === 'acessos' && (
        <div className="overflow-x-auto rounded-xl ring-1 ring-slate-800">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-900 text-slate-400">
              <tr><th className="p-2">Quando</th><th className="p-2">Aluno</th><th className="p-2">Resultado</th><th className="p-2">Meio</th><th className="p-2">Score</th><th className="p-2">Motivo</th></tr>
            </thead>
            <tbody>
              {acessos.map((a) => (
                <tr key={a.id} className="border-t border-slate-800">
                  <td className="p-2 whitespace-nowrap">{new Date(a.dataHora).toLocaleString('pt-BR')}</td>
                  <td className="p-2">{nomeDe(a.alunoId)}</td>
                  <td className={`p-2 ${a.resultado === 'LIBERADO' ? 'text-emerald-400' : 'text-rose-300'}`}>{a.resultado}</td>
                  <td className="p-2">{a.meio}</td>
                  <td className="p-2 tabular-nums">{a.score?.toFixed(2) ?? '—'}</td>
                  <td className="p-2 text-slate-400">{a.motivo ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {aba === 'alunos' && (
        <ul className="space-y-2">
          {alunos.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-900 p-3 ring-1 ring-slate-800">
              <div>
                <p className="font-medium">{a.nome}</p>
                <p className="text-xs text-slate-400">
                  CPF {a.cpf} · biometria {a.consentimentoBiometricoEm ? 'autorizada' : 'sem consentimento'}
                  {a.bloqueado && <span className="text-rose-300"> · bloqueado: {a.motivoBloqueio}</span>}
                </p>
              </div>
              <div className="flex gap-2 text-sm">
                {a.bloqueado
                  ? <button onClick={() => acao(() => apiJson(`/alunos/${a.id}/bloqueio`, { method: 'DELETE' }))} className="rounded bg-slate-800 px-2 py-1">Desbloquear</button>
                  : <button onClick={() => { const m = prompt('Motivo do bloqueio'); if (m) acao(() => apiJson(`/alunos/${a.id}/bloqueio`, { method: 'POST', body: JSON.stringify({ motivo: m }) })) }} className="rounded bg-slate-800 px-2 py-1">Bloquear</button>}
                {a.consentimentoBiometricoEm && (
                  <button onClick={() => { if (confirm(`Apagar a biometria de ${a.nome}?`)) acao(() => apiJson(`/alunos/${a.id}/biometria`, { method: 'DELETE' })) }}
                    className="rounded bg-slate-800 px-2 py-1 text-rose-300">Remover biometria</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {aba === 'planos' && (
        <ul className="grid gap-2 sm:grid-cols-2">
          {planos.map((p) => (
            <li key={p.id} className="rounded-xl bg-slate-900 p-3 ring-1 ring-slate-800">
              <p className="font-medium">{p.nome}</p>
              <p className="text-xs text-slate-400">{p.inicio.slice(0, 5)}–{p.fim.slice(0, 5)} · {p.dias.length} dias/semana · {p.acessosPorSemana ? `${p.acessosPorSemana}x por semana` : 'sem limite'}</p>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
```

`App.tsx`:

```tsx
import { Route, Routes } from 'react-router-dom'
import { Painel } from './painel/Painel'
import { Totem } from './Totem'

export default function App() {
  return (
    <Routes>
      <Route path="/painel" element={<Painel />} />
      <Route path="*" element={<Totem />} />
    </Routes>
  )
}
```

- [ ] **Step 4: Build e verificação**

Run: `npm run build && npm test` → Expected: sem erros; 3 testes passam.

No navegador embutido, com compose + seed no ar:
- `/painel`: login com `admin@facegym.dev` / `admin12345` → aba Acessos mostra os check-ins da Task 4; bloquear e desbloquear um aluno reflete no totem.
- Totem → "Quero testar": marcar consentimento → sem câmera no navegador embutido deve aparecer a mensagem de câmera indisponível (Review Focus 4) e a galeria continua funcionando.
- A câmera real é testada pelo usuário no próprio navegador (Task 6 Step 6).

- [ ] **Step 5: Commit**

```bash
git add facegym-web
git commit -m "feat(web): Teste com você pela câmera e painel da academia"
```

---

### Task 6: Deploy (Neon + Render + Vercel), seed de produção e README

**Files:**
- Create: `render.yaml` (raiz)
- Create: `README.md` (raiz)

- [ ] **Step 1: Neon (usuário)**

O usuário cria no Neon um projeto `facegym` (região São Paulo, `aws-sa-east-1`) com dois databases: `facegym` e `biometria`, e passa as duas connection strings. O pgvector é habilitado pelo próprio serviço (`CREATE EXTENSION IF NOT EXISTS vector` no `init_schema`).

- [ ] **Step 2: Blueprint do Render**

```yaml
# render.yaml — API e biometria como web services Docker (plano free). Bancos no Neon.
services:
  - type: web
    name: facegym-biometria
    runtime: docker
    rootDir: facegym-biometria
    plan: free
    healthCheckPath: /health
    envVars:
      - key: INTERNAL_KEY
        generateValue: true
      - key: DATABASE_URL
        sync: false  # connection string do database "biometria" no Neon

  - type: web
    name: facegym-api
    runtime: docker
    rootDir: facegym-api
    plan: free
    healthCheckPath: /actuator/health
    envVars:
      - key: SPRING_DATASOURCE_URL
        sync: false  # jdbc:postgresql://<host>/facegym?sslmode=require
      - key: SPRING_DATASOURCE_USERNAME
        sync: false
      - key: SPRING_DATASOURCE_PASSWORD
        sync: false
      - key: BIOMETRIA_URL
        value: https://facegym-biometria.onrender.com
      - key: BIOMETRIA_KEY
        fromService: { type: web, name: facegym-biometria, envVarKey: INTERNAL_KEY }
      - key: JWT_SECRET
        generateValue: true
      - key: ADMIN_EMAIL
        value: admin@facegym.dev
      - key: ADMIN_PASSWORD
        sync: false
      - key: CORS_ALLOWED_ORIGINS
        value: https://facegym-web.vercel.app
      - key: JAVA_TOOL_OPTIONS
        value: -XX:MaxRAMPercentage=75 -XX:+UseSerialGC
```

Commit e push: criar o repositório `guirodriguesxz/facegym` público (`gh repo create guirodriguesxz/facegym --public --source . --push`) — **confirmar com o usuário antes** (publica o código).

- [ ] **Step 3: Render (usuário)** — abrir `https://render.com/deploy?repo=https://github.com/guirodriguesxz/facegym`, preencher `DATABASE_URL`, `SPRING_DATASOURCE_*` e `ADMIN_PASSWORD`, aplicar. Verificar:

```bash
curl -s -m 120 https://facegym-biometria.onrender.com/health
curl -s -m 120 https://facegym-api.onrender.com/actuator/health
```

Expected: `{"status":"UP"}` e `"status":"UP"` com `circuito`.

- [ ] **Step 4: Seed de produção**

```bash
API=https://facegym-api.onrender.com/api/v1 ADMIN_PASSWORD='<senha definida no Render>' scripts/seed-demo.sh
for s in ana bruno carla diego elisa; do printf "%s: " $s; curl -s -m 120 -X POST https://facegym-api.onrender.com/api/v1/check-ins -F foto=@facegym-web/public/demo/$s.jpg; echo; done
```

Expected: os 5 resultados da Task 3 Step 6.

- [ ] **Step 5: Vercel (usuário)** — importar `guirodriguesxz/facegym` com **Root Directory `facegym-web`**, nome `facegym-web`, variável `VITE_API_URL=https://facegym-api.onrender.com/api/v1` como **não sensível**. Verificar o bundle:

```bash
JS=$(curl -s https://facegym-web.vercel.app/ | grep -o '/assets/index-[^"]*\.js'); curl -s https://facegym-web.vercel.app$JS | grep -o 'facegym-api.onrender.com[^`"]*' | head -1
```

Expected: `facegym-api.onrender.com/api/v1`.

- [ ] **Step 6: Teste com você de verdade (usuário)** — o usuário abre `https://facegym-web.vercel.app`, clica em "Quero testar", cadastra o rosto e faz check-in: deve aparecer "Bem-vinda(o), Visitante XXXX!". Depois "Apagar meus dados agora" e novo check-in → "Rosto não reconhecido".

- [ ] **Step 7: README**

```markdown
# FaceGym

Check-in de academia por reconhecimento facial. Projeto de portfólio com foco em **arquitetura
hexagonal** e **integração resiliente** entre serviços.

**Demo:** [facegym-web.vercel.app](https://facegym-web.vercel.app) — clique nos alunos fictícios ou use
"Teste com você" para ser reconhecido pela câmera (seus dados somem em 10 minutos).
Painel: `/painel` com `admin@facegym.dev` / *(senha de demonstração)*. Os serviços dormem no plano
free: o primeiro acesso pode levar ~1 minuto.

## Arquitetura

```
facegym-web (React)  ──►  facegym-api (Spring, hexagonal)  ──►  facegym-biometria (FastAPI + InsightFace)
                              │  domain: regras de acesso            │  só embeddings (pgvector)
                              │  application: casos de uso + portas  │  nunca guarda foto
                              └  adapters: REST, JDBC, cliente HTTP  └
```

- **Regras de acesso** em Java puro (`domain`): bloqueio, plano ativo, horário do plano, limite semanal.
  ArchUnit quebra o build se o domínio importar Spring, JDBC ou HTTP.
- **Resiliência**: timeout de 2 s, 1 retry só em erro de rede, circuit breaker (Resilience4j). Se a
  biometria cair, o totem pede o CPF, que passa pelas mesmas regras.
- **Limiares calibrados** no LFW (`facegym-biometria/CALIBRATION.md`): aceite 0.41, dúvida 0.18.

## Privacidade (LGPD)

- Biometria em banco separado, só com o vetor do rosto ligado a um id; nada de nome, CPF ou foto.
- Cadastro de biometria exige consentimento registrado; remover apaga o vetor e revoga o consentimento.
- "Teste com você": consentimento explícito, exclusão automática em 10 min e botão de apagar na hora.
- Rostos da demo são fictícios, gerados por IA (`scripts/demo-fotos/LICENSE.md`).

## Rodando localmente

```bash
docker compose up -d --build
ADMIN_PASSWORD=admin12345 scripts/seed-demo.sh
cd facegym-web && cp .env.example .env && npm i && npm run dev
```

## Testes

- API: `cd facegym-api && ./mvnw verify` (domínio, casos de uso, JDBC com Testcontainers, WireMock, fluxo completo)
- Biometria: `cd facegym-biometria && pytest` (+ `pytest -m slow` para a acurácia no LFW)
- Web: `cd facegym-web && npm test`

## Próximos passos

Antipassback, detecção de vivacidade, pagamentos, multi-tenant, app do aluno.
```

(O bloco de arquitetura é texto, não código: no arquivo final, usar a cerca de três crases normalmente.)

- [ ] **Step 8: Commit e push**

```bash
git add render.yaml README.md
git commit -m "docs: README e blueprint do Render"
git push
```
