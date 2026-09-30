# FaceGym

Check-in de academia por reconhecimento facial. Projeto de portfólio com foco em **arquitetura
hexagonal** e **integração resiliente** entre serviços.

**Demo:** [facegym-web.vercel.app](https://facegym-web.vercel.app) — clique nos alunos fictícios ou use
"Teste com você" para ser reconhecido pela câmera (seus dados somem em 10 minutos).
Painel: [/painel](https://facegym-web.vercel.app/painel) com `admin@facegym.dev` / `demo12345`.
Os serviços dormem no plano free: o primeiro acesso pode levar ~1 minuto, e cada check-in leva alguns
segundos porque a biometria roda com 10% de uma CPU (localmente leva menos de 1 s).

## Arquitetura

```
facegym-web (React)  ──►  facegym-api (Spring, hexagonal)  ──►  facegym-biometria (FastAPI + InsightFace)
                              │  domain: regras de acesso            │  só embeddings (pgvector)
                              │  application: casos de uso + portas  │  nunca guarda foto
                              └  adapters: REST, JDBC, cliente HTTP  └
```

- **Regras de acesso** em Java puro (`domain`): bloqueio, antipassback (5 min entre entradas, `ANTIPASSBACK`),
  plano ativo, horário do plano, limite semanal.
  ArchUnit quebra o build se o domínio importar Spring, JDBC ou HTTP.
- **Resiliência**: timeout configurável (2 s local, 8 s em produção por causa da CPU do plano free), 1 retry só em erro de rede, circuit breaker (Resilience4j). Se a
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

Detecção de vivacidade, pagamentos, multi-tenant, app do aluno.
