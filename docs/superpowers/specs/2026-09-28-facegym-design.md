# FaceGym — Design

Data: 2026-09-28 · Status: aguardando revisão

## 1. Objetivo

Projeto de portfólio para vagas de **back-end Java/Spring (júnior/estágio)**. Check-in de academia por
reconhecimento facial, onde o reconhecimento é o chamariz e o peso técnico está na API Spring.

Deve destacar o que o portfólio atual (physiomanage) ainda não mostra:

- **Arquitetura hexagonal / DDD** — regra de acesso isolada de framework e de tecnologia de identificação.
- **Integração resiliente entre serviços** — timeout, retry, circuit breaker e fallback por CPF.

Sucesso = demo pública funcionando, README que explica as decisões (arquitetura, resiliência, LGPD) e
suíte de testes que prova as regras e a resiliência.

## 2. Componentes

Monorepo `facegym` com `docker-compose` que sobe tudo.

### 2.1 `facegym-api` — Spring Boot 3, Java 21 (hexagonal)

- `domain` (Java puro): `Aluno`, `Plano`, `Matricula`, `Acesso`, `PoliticaDeAcesso` e as regras
  `PlanoAtivo`, `HorarioDoPlano`, `LimiteSemanal`, `BloqueioManual`. Cada regra retorna
  `Libera` ou `Nega(motivo)`.
- `application`: casos de uso `RealizarCheckIn`, `CadastrarAluno`, `CadastrarBiometria`,
  `RemoverBiometria`, `BloquearAluno`, `CadastrarPlano`, `Matricular`. Dependem apenas de portas:
  `ReconhecimentoFacial`, `Alunos`, `Planos`, `Matriculas`, `RegistroDeAcessos`, `Relogio`.
- `adapters`: REST (controllers), JPA/Postgres, `BiometriaHttpClient` (Resilience4j), JWT do painel.
- **ArchUnit** falha o build se `domain` ou `application` importarem Spring/JPA/HTTP.

### 2.2 `facegym-biometria` — Python, FastAPI

- Modelo InsightFace `buffalo_s` (ONNX, CPU); embeddings de 512 dimensões.
- Postgres + pgvector próprio. Tabela `face_embedding(aluno_id, embedding vector(512), criado_em)`.
- Endpoints (todos exigem header `X-Internal-Key`):
  - `PUT /faces/{alunoId}` — recebe imagem, grava embedding (substitui existente).
  - `POST /faces/identify` — recebe imagem, retorna `{alunoId, score}` do vizinho mais próximo
    (similaridade de cosseno) ou `{alunoId: null}` se não há rosto/nenhum cadastro.
  - `DELETE /faces/{alunoId}` — apaga embedding (idempotente).
  - `POST /faces/compare-demo` — identifica sem nenhuma escrita (usado pela câmera do visitante).
- Nunca persiste nem loga imagem ou embedding. Cadastro (`PUT`) de imagem sem rosto → 422; identify/compare-demo
  sem rosto → `{alunoId: null}`; mais de um rosto → usa o maior.
- Limiares 0.80/0.60 são valores iniciais: o plano 1 calibra os valores reais do modelo (`CALIBRATION.md`).

### 2.3 `facegym-web` — React + TypeScript

- **Totem** (público): galeria de fotos de exemplo (clicar/arrastar) + botão opcional "usar minha câmera";
  mostra resultado (liberado / negado com motivo / confirme CPF / não reconhecido → CPF).
- **Painel** (admin, login): alunos, planos, matrículas, bloqueio, cadastro/remoção de biometria com
  consentimento, histórico de acessos com motivo e meio.

## 3. Fluxo de check-in

1. Totem envia foto → `POST /api/v1/check-ins` (multipart).
2. `RealizarCheckIn` chama `ReconhecimentoFacial.identificar(foto)`.
3. Limiar (configurável):
   - `score >= 0.80` → identificado → `PoliticaDeAcesso` avalia as 4 regras.
   - `0.60 <= score < 0.80` → resposta `CONFIRMAR_CPF` com token de check-in pendente (válido 60 s);
     o totem envia `POST /api/v1/check-ins/{token}/cpf`. CPF precisa bater com o aluno sugerido.
   - `< 0.60` ou sem rosto → `NAO_RECONHECIDO`; totem oferece `POST /api/v1/check-ins/cpf`.
4. Toda tentativa grava `Acesso(dataHora, alunoId?, resultado, motivo, meio FACIAL|CPF, score?)`.
5. Regras avaliadas em ordem fixa: `BloqueioManual`, `PlanoAtivo`, `HorarioDoPlano`, `LimiteSemanal`;
   a primeira negação define o motivo.

### Regras

| Regra | Nega quando |
|---|---|
| BloqueioManual | aluno com `bloqueado = true` (motivo do admin é exibido) |
| PlanoAtivo | sem matrícula vigente na data (vencimento inclusivo: vence dia 10 → entra dia 10) |
| HorarioDoPlano | dia da semana fora do plano, ou hora fora de `[inicio, fim)` no fuso `America/Sao_Paulo` |
| LimiteSemanal | acessos **liberados** na semana ISO (segunda 00:00 a domingo 23:59, fuso da academia) ≥ limite |

Só acessos liberados contam para o limite. Horário e semana usam a porta `Relogio` (testável).

## 4. Resiliência (chamada à biometria)

- Timeout 2 s; 1 retry apenas em erro de conexão/timeout (não em 4xx).
- Circuit breaker (Resilience4j): abre com 50% de falha em janela de 10 chamadas; meia-abertura após 30 s.
- Com o circuito aberto ou falha final → resposta `BIOMETRIA_INDISPONIVEL`; totem cai direto no CPF.
- Estado do circuito exposto em `/actuator/health` e métrica Micrometer.
- Meta: check-in < 1,5 s com o serviço aquecido.

## 5. Dados e LGPD

- Banco da API: `aluno(nome, cpf, email, bloqueado, motivo_bloqueio, consentimento_biometrico_em)`,
  `plano(nome, preco, dias_semana, hora_inicio, hora_fim, acessos_semana?)`,
  `matricula(aluno, plano, inicio, vencimento)`, `acesso(...)`, `admin(email, senha_hash)`.
- Banco da biometria: apenas `face_embedding`. Sem nome, CPF ou foto.
- `CadastrarBiometria` exige consentimento registrado (regra de domínio).
- Foto processada só em memória. Logs sem imagem/embedding.
- `RemoverBiometria` apaga o embedding e revoga o consentimento; acesso por CPF continua.
- Endpoint de demo (`compare-demo`) não grava nada; README documenta.
- Seção "Privacidade" no README com essas decisões.

## 6. Dados de demonstração

5 alunos fictícios, um por cenário: em dia, plano vencido, fora do horário, limite semanal atingido,
bloqueado. Fotos de dataset com licença que permita redistribuição (ou rostos gerados por IA);
licença confirmada e citada no README antes do uso.

## 7. Testes

- **Domínio**: unitários puros de cada regra e da política, incluindo bordas (12:00 em plano 6–12,
  virada de semana, dia do vencimento, fuso).
- **Casos de uso**: portas falsas em memória (`ReconhecimentoFacial` com score fixo) cobrindo os 3 limiares.
- **Resiliência**: WireMock para lentidão, queda e 5xx → timeout, retry, circuito aberto, fallback.
- **Integração**: Testcontainers (Postgres) com fluxo completo por HTTP.
- **Arquitetura**: ArchUnit.
- **Biometria**: pytest de cadastrar/identificar/apagar e teste de acurácia com as fotos de exemplo
  (mesma pessoa ≥ limiar; pessoas diferentes < limiar).
- CI no GitHub Actions para API, biometria e build do front.

## 8. Deploy

Vercel (front) e Render (API + biometria, cada um com Postgres). **Risco**: memória do modelo no plano
free (512 MB). **Primeira tarefa do plano**: medir memória e latência do `buffalo_s` num container;
se não couber, trocar por modelo menor antes de seguir.

## 9. Fora do escopo (v1)

Antipassback, detecção de vivacidade, pagamentos reais, multi-tenant, app mobile do aluno,
refresh token no painel.
