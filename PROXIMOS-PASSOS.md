# Próximos passos — revisão de segurança (2026-09-29)

Resumo da sessão de revisão de segurança e do que ainda falta. Nada disso foi testado com Docker
nem com pessoas reais: ver "Pendências".

## O que mudou

| # | Problema | Correção | Onde |
|---|---|---|---|
| 1 | `/demo/identificar` público fazia busca facial em todos os alunos | Endpoint removido | `CheckInController`, `SecurityConfig` |
| 2 | Check-in por CPF revelava nome e motivo | Resposta sem nome/motivo; motivo só no registro de acessos | `RealizarCheckIn.porCpf` |
| 3 | Sem limite de tentativas | Limite por IP (login, CPF, foto, desafio, visitantes, aquecer); janela por regra, teto de 100 mil chaves, caminho decodificado (`getServletPath`) | `adapters/web/LimiteDeTentativas.java` |
| 4 | Sem prova de vida | Desafio sorteado pelo servidor (virar o rosto para ESQUERDA/DIREITA, 20 s, uso único) + giro 3D pelos pontos do rosto + anti-spoofing passivo (MiniFASNet ONNX) | `DesafiosDeVida`, `facegym-biometria/app/liveness.py`, `app/antispoof.py`, `models/`, `Camera.tsx` |
| 5 | Actuator expunha detalhes e métricas | `show-details: when-authorized`; `/actuator/prometheus` exige login | `application.yml`, `SecurityConfig` |
| 6 | Chave padrão da biometria no código | `BIOMETRIA_KEY` obrigatória (≥ 16 caracteres) | `BiometriaProperties` |
| 7 | Qualquer um apagava visitante | Segredo por visitante (só o SHA-256 no banco), cabeçalho `X-Visitante-Segredo` | `VisitantesTemporarios`, migração `V3` |
| — | CPF sozinho liberava aluno real | PIN de 4–6 dígitos (BCrypt), 5 erros bloqueiam 15 min, recusas com o mesmo custo de tempo | `PinDoAluno`, `TentativasDePinEmMemoria` |
| — | Qualquer cliente enviava fotos à API | Totens registrados (token de 256 bits, só o hash no banco, header `X-Totem-Token`). Sem totem, só alunos da demo e visitantes | `TotensJdbc`, `Publico`, migração `V4`, aba "Totens" no painel |

A galeria demo continua funcionando no site público: os CPFs em `FACEGYM_DEMO_CPFS`
(`render.yaml`, `docker-compose.yml`) passam sem totem, sem PIN e sem prova de vida.

## Pendências

### 1. Rodar os testes que precisam de Docker
Sem Docker no Windows, estes não rodaram: `JdbcAdaptersTest` (inclui testes novos de totens e
PINs) e `FluxoCompletoIT` (atualizado para totem, PIN, desafio e segredo de visitante).

```bash
cd facegym-api && ./mvnw verify
```

Biometria (testes rápidos já passaram; os de repositório precisam de Docker):

```bash
cd facegym-biometria && pip install -e ".[dev]" && pytest
```

### 2. Calibrar a prova de vida com pessoas reais
Nada foi validado com rostos reais. Com 2–3 pessoas, numa webcam de verdade:

- **Sinal do lado** (`facegym-biometria/app/liveness.py`, `SINAL`): a hipótese é que virar para a
  *própria esquerda* dá `yaw > 0` na foto sem espelho. Se estiver invertido, **todo check-in legítimo
  é barrado**: basta trocar os sinais.
- **Limiares de giro**: `FRENTE_MAX = 0.15`, `VIRADO_MIN = 0.30`, `MESMA_PESSOA_MIN = 0.30`.
- **Anti-spoofing** (`app/antispoof.py`, `REAL_MIN = 0.5`): medir notas de rostos reais e de ataques
  (foto impressa, celular e monitor na frente da câmera). O modelo foi treinado com câmera de
  celular; webcam e iluminação podem mudar muito o resultado.

### 3. Confirmar o IP real atrás do proxy do Render
`server.forward-headers-strategy: native` só confia em proxies com IP privado. Se o proxy do Render
usar outro IP, todos os clientes dividem o mesmo contador de tentativas (um atacante bloquearia
todo mundo). Conferir depois do deploy, por exemplo logando `request.getRemoteAddr()` em uma
requisição de teste.

### 4. Deploy
- Migrações novas: `V3__visitante_segredo.sql`, `V4__totem_e_pin.sql` (o Flyway aplica sozinho).
- API e frontend precisam subir juntos: o frontend manda o segredo do visitante, o desafio e o
  token do totem.
- Depois do deploy: no painel, cadastrar os totens (abrir `/#totem=<token>` no aparelho) e definir
  o PIN de cada aluno real.
- Variáveis novas ou obrigatórias: `BIOMETRIA_KEY` (já no `render.yaml`), `FACEGYM_DEMO_CPFS`.

## Riscos conhecidos que continuam

- **Token do totem**: quem roubar o aparelho leva o token. Revogar no painel (aba Totens).
  Para instalação real, considerar certificado de cliente ou restrição física.
- **Prova de vida**: vídeo ao vivo ou manipulação em tempo real de alta qualidade ainda podem
  enganar o modelo. Contra isso, só hardware com sensor de profundidade ou infravermelho.
- **Estado em memória** (limites, desafios, confirmações, tentativas de PIN): vale para uma única
  instância. Com mais de uma, mover para Redis ou banco.
- **CPFs da demo** têm dígitos válidos: se um aluno real com um deles for cadastrado, passaria sem
  prova de vida e sem totem.

## Limpeza opcional

- `ReconhecimentoFacial.compararDemo`, `BiometriaHttpClient.compararDemo` e a rota
  `/faces/compare-demo` da biometria ficaram sem uso depois da remoção de `/demo/identificar`.
