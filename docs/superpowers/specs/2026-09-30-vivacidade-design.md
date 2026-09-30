# Detecção de vivacidade — design

**Data:** 2026-09-30 · **Status:** aprovado em conversa, aguardando revisão da spec

## Objetivo

Vitrine de portfólio: mostrar que o FaceGym trata fraude com foto na frente da câmera, com uma
experiência visível na demo. Segurança razoável, não de nível bancário.

**Sucesso:** no totem, "Teste com você" pede um desafio (virar o rosto para um lado sorteado);
uma foto parada ou um vídeo gravado antes não passa; a decisão fica no domínio Java e aparece
no histórico de acessos.

## Restrições

- Biometria no plano free do Render: 512 MB de RAM e ~10% de CPU. Nenhum modelo novo.
- Arquitetura hexagonal: o serviço Python mede, o domínio Java decide (ArchUnit continua valendo).
- Alunos fictícios da demo enviam foto estática e precisam continuar funcionando.

## Abordagem

Desafio ativo conferido no servidor. Descartados: modelo passivo anti-spoofing (memória/CPU,
invisível na demo) e vivacidade só no navegador (burlável chamando a API).

## Fluxo e contratos

1. `POST /api/v1/check-ins/desafios` → `{token, lado: "ESQUERDA"|"DIREITA"}`. Lado sorteado,
   uso único, validade 30 s, guardado em memória (mesmo padrão de `CheckInsPendentes`).
2. O totem captura a foto de frente, mostra a instrução e, após contagem de 3 s, a foto virada.
3. `POST /api/v1/check-ins` (multipart) com `foto`, `fotoVirada` e `desafio`.
4. A API consome o desafio e chama a biometria **uma vez** com as duas fotos.
5. A política de vivacidade (domínio) decide; se aprovada, segue o fluxo atual (limiares de
   aceite/dúvida, confirmação por CPF, regras de acesso).

### Biometria: `POST /faces/identify-liveness`

- Multipart com `image` (frente) e `turned` (virada); mesma chave interna e limite de 5 MB por imagem.
- Resposta: `{alunoId, score, giroFrente, giroVirada, similaridade}`. `alunoId`/`score` vêm da foto
  de frente (mesma busca do `identify`). Se faltar rosto em qualquer uma das fotos, os campos de
  medida vêm `null` e `alunoId` também.
- **Giro:** com os 5 pontos do RetinaFace (olho esq., olho dir., nariz, boca esq., boca dir.),
  `giro = (nariz.x − meio_dos_olhos.x) / distância_entre_olhos`, com sinal convertido para o
  ponto de vista da pessoa (positivo = virou para a esquerda dela). A foto chega sem espelhamento.
- **Similaridade:** cosseno entre os embeddings normalizados das duas fotos.
- O serviço não decide nada nem guarda as imagens.

### Modo `VIVACIDADE` (`facegym.vivacidade.modo`)

- `obrigatoria` (padrão): `/check-ins` sem `fotoVirada`/`desafio` → negado, "Prova de vida obrigatória".
- `opcional` (demo pública e `docker-compose.yml` local, onde os alunos fictícios também são usados): foto única segue funcionando e o acesso fica
  com `vivacidade = false`. Nesse modo a vivacidade é burlável por quem envia foto única; o README
  documenta essa troca.
- Em ambos os modos, desafio enviado e reprovado → negado.

## Domínio

- `PoliticaDeVivacidade` (Java puro) recebe `LadoDesafio`, `giroFrente`, `giroVirada`,
  `similaridade` e os limiares; devolve aprovada ou reprovada.
- Limiares iniciais (configuráveis em `facegym.vivacidade.*`), a calibrar:
  - frente: `|giroFrente| < 0,15`
  - virada: `giroVirada ≥ 0,25` no sentido do lado pedido
  - mesma pessoa: `similaridade ≥ 0,30`
- Nova porta `DesafiosDeVivacidade` (criar/consumir), adaptador em memória.
- `ReconhecimentoFacial` ganha `identificarComVivacidade(frente, virada)` → `IdentificacaoComVivacidade`.
- `Acesso` ganha `Boolean vivacidade` (`null` quando não se aplica, como check-in por CPF).

## Erros

| Situação | Resultado |
|---|---|
| Desafio expirado, já usado ou inexistente | Negado: "Desafio expirado, tente de novo" |
| Nenhum rosto numa das fotos | Não reconhecido (como hoje) |
| Não estava de frente, não virou ou virou para o lado errado | Negado: "Prova de vida não confirmada" |
| Fotos de pessoas diferentes | Negado: "Prova de vida não confirmada" (mesma mensagem, para não ajudar a burlar) |
| Biometria fora do ar | Como hoje: circuit breaker e CPF no totem; CPF não passa por vivacidade |

Negações são registradas no histórico com `vivacidade = false`.

## Persistência

Migração `V3__vivacidade.sql`: `ALTER TABLE acesso ADD COLUMN vivacidade boolean` (nulo permitido;
acessos antigos ficam `null`).

## Web

- Totem/"Teste com você": pede o desafio ao detectar o rosto, captura a frente, mostra a seta
  "Vire o rosto para a **esquerda** ←", contagem de 3 s, captura a virada, "Conferindo prova de vida…".
- Resultado no terminal da catraca com selo "✓ Prova de vida" ou "Sem prova de vida (demo)".
- Reprovação: "Não deu para confirmar — tente de novo" com botão para repetir.
- Alunos fictícios continuam enviando foto única.
- Painel: coluna "Prova de vida" (✓ / —) no histórico de acessos.

## Calibração

O LFW não tem pares de rostos virados. Um passo do plano mede giro e similaridade em selfies
reais (frente, esquerda e direita) pela webcam do autor; os valores e os limiares escolhidos
ficam em `facegym-biometria/CALIBRATION.md`. Até lá, os limiares acima são estimativa.

## Testes

- **Biometria (pytest):** giro com pontos sintéticos (frente, esquerda, direita, sinal); endpoint
  novo com embedder falso (sem rosto, uma foto sem rosto, sucesso); teste `slow` com fotos da calibração.
- **Domínio:** `PoliticaDeVivacidade` nos limites de cada limiar, lado errado, pessoa diferente.
- **Caso de uso:** desafio válido, expirado e reutilizado; modos `obrigatoria` e `opcional`;
  reprovação registrada.
- **Adaptadores:** cliente HTTP com WireMock; coluna nova no JDBC (Testcontainers); fluxo completo.
- **Web:** mapeamento de resultados (selos e mensagens).

## Custo

Duas detecções e dois embeddings por check-in com desafio: ~4 s no plano free. O timeout de
produção (8 s) cobre, com pouca folga.

## Fora do escopo

Modelo passivo anti-spoofing, detecção de piscada, vivacidade no cadastro da biometria.
