# Detecção de vivacidade — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** check-in pela câmera com desafio ativo (virar o rosto para um lado sorteado), medido pela biometria e decidido no domínio Java, com modo `obrigatoria`/`opcional`.

**Architecture:** a biometria (FastAPI) ganha `POST /faces/identify-liveness`, que devolve giro do rosto (dos 5 pontos do RetinaFace) e similaridade entre as duas fotos, sem decidir nada. A API ganha um desafio de uso único (porta + adaptador em memória), a `PoliticaDeVivacidade` no domínio e um novo caminho em `RealizarCheckIn`. O totem pede o desafio, captura frente e virada e mostra o resultado com selo.

**Tech Stack:** Python 3 / FastAPI / InsightFace (biometria); Java 21 / Spring Boot / JDBC / Flyway / WireMock / Testcontainers (API); React + Vite + Vitest (web).

**Spec:** `docs/superpowers/specs/2026-09-30-vivacidade-design.md`

## Global Constraints

- Nenhum modelo novo na biometria; 512 MB de RAM e ~10% de CPU no plano free.
- O serviço Python mede, o domínio Java decide; `ArquiteturaTest` (ArchUnit) tem que continuar passando.
- Endpoint da biometria: `POST /faces/identify-liveness`, partes `image` (frente) e `turned` (virada); resposta `{alunoId, score, giroFrente, giroVirada, similaridade}`.
- Giro: `(nariz.x − meio_dos_olhos.x) / distância_entre_olhos`, positivo = a pessoa virou para a esquerda dela; foto sem espelhamento.
- Desafio: `POST /api/v1/check-ins/desafios` → `{token, lado: "ESQUERDA"|"DIREITA"}`, uso único, validade 30 s.
- Limiares iniciais: frente `|giro| < 0.15`; virada `giro ≥ 0.25` no lado pedido; mesma pessoa `similaridade ≥ 0.30`.
- Modo `facegym.vivacidade.modo` = `${VIVACIDADE:obrigatoria}`; `opcional` no `docker-compose.yml` e no `render.yaml`.
- Mensagens exatas: "Prova de vida obrigatória", "Desafio expirado, tente de novo", "Prova de vida não confirmada".
- Coluna `acesso.vivacidade boolean` (nula permitida), migração `V3__vivacidade.sql`.
- Selos no totem: "✓ Prova de vida" e "Sem prova de vida (demo)".
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Ajustes à spec decididos no plano

1. **Reprovação não revela o nome.** Se a prova de vida falha, a API ainda não confiou na identificação; responder `NEGADO` com o nome entregaria quem é a pessoa da foto. Por isso as três negações de vivacidade usam um status próprio, `PROVA_DE_VIDA_REPROVADA` (sem `nome`, com `motivo`), registrado no histórico como `NEGADO`, `vivacidade = false`, `aluno_id` nulo.
2. **O desafio começa no botão** "Fazer check-in", não na detecção automática do rosto (o navegador não detecta rosto hoje).
3. **Selfies da calibração não entram no repositório** (LGPD). O teste `slow` usa as fotos fictícias da demo (frontais) para conferir o giro com o modelo real; as selfies ficam numa pasta local e só os números vão para o `CALIBRATION.md`.

## Review Focus

1. **Desafio reutilizado (replay):** mandar o mesmo token duas vezes → a segunda é "Desafio expirado, tente de novo", sem chamar a biometria. Teste na Task 6.
2. **Virou para o lado errado ou mandou a virada como frente:** reprovado. Testes na Task 3.
3. **Duas fotos no mesmo corpo:** a biometria recusava corpo > 5,5 MB e o Spring > 6 MB; com duas fotos o limite precisa acomodar 2 × 5 MB. Testes na Task 2 (biometria) e config na Task 7.
4. **Reprovação sem nome na resposta:** o JSON de `PROVA_DE_VIDA_REPROVADA` não tem `nome`. Teste na Task 7.
5. **Desafio sem `fotoVirada`:** o token é consumido e o check-in reprovado, sem erro 500. Teste na Task 6.

---

## Estrutura de arquivos

**Biometria**
- Create `facegym-biometria/app/liveness.py` — função pura `giro(kps)`.
- Modify `facegym-biometria/app/embedder.py` — `Rosto` (embedding + pontos) e `analyze()`.
- Modify `facegym-biometria/app/api.py` — endpoint novo e limite de corpo por rota.
- Create `facegym-biometria/tests/test_liveness.py`, `tests/test_liveness_real.py` (slow); modify `tests/conftest.py`, `tests/test_api.py`.
- Create `facegym-biometria/scripts/calibrar_vivacidade.py`; modify `CALIBRATION.md`.

**API**
- Create `domain/vivacidade/{LadoDesafio, MedidasDeVivacidade, LimiaresDeVivacidade, PoliticaDeVivacidade}.java`.
- Create `application/port/{DesafiosDeVivacidade, IdentificacaoComVivacidade}.java`, `application/ConfiguracaoDeVivacidade.java`.
- Create `adapters/memoria/DesafiosDeVivacidadeEmMemoria.java`, `db/migration/V3__vivacidade.sql`.
- Modify `ReconhecimentoFacial`, `CheckInsPendentes`, `CheckInsPendentesEmMemoria`, `BiometriaHttpClient`, `Acesso`, `AcessosJdbc`, `ResultadoCheckIn`, `RealizarCheckIn`, `CheckInController`, `UseCaseConfig`, `application.yml`.

**Web**
- Create `facegym-web/src/desafio.ts`, `desafio.test.ts`.
- Modify `resultado.ts`, `resultado.test.ts`, `Camera.tsx`, `Catraca.tsx`, `Totem.tsx`, `painel/Painel.tsx`.

**Deploy/docs:** `docker-compose.yml`, `render.yaml`, `README.md`.

Caminhos Java abaixo são relativos a `facegym-api/src/main/java/com/facegym/` (código) e `facegym-api/src/test/java/com/facegym/` (testes).

---

### Task 1: Giro do rosto e análise com pontos na biometria

**Files:**
- Create: `facegym-biometria/app/liveness.py`
- Modify: `facegym-biometria/app/embedder.py`
- Test: `facegym-biometria/tests/test_liveness.py`

**Interfaces:**
- Produces: `app.liveness.giro(kps: np.ndarray) -> float`; `app.embedder.Rosto(embedding: np.ndarray, kps: np.ndarray)`; `Embedder.analyze(image_bgr) -> Rosto | None` (além de `embed`).

- [ ] **Step 1: Write the failing test**

`facegym-biometria/tests/test_liveness.py`:
```python
import numpy as np
import pytest
from app.liveness import giro

def pontos(nariz_x: float, olho_a=(40.0, 50.0), olho_b=(80.0, 50.0)) -> np.ndarray:
    """5 pontos do RetinaFace na ordem da imagem: olho esq., olho dir., nariz, boca esq., boca dir."""
    return np.array([olho_a, olho_b, (nariz_x, 70.0), (45.0, 90.0), (75.0, 90.0)], dtype=np.float32)

def test_de_frente_e_zero():
    assert giro(pontos(60.0)) == pytest.approx(0.0)

def test_virar_para_a_esquerda_da_pessoa_e_positivo():
    # foto sem espelhamento: a esquerda da pessoa fica à direita da imagem
    assert giro(pontos(76.0)) == pytest.approx(0.4)

def test_virar_para_a_direita_da_pessoa_e_negativo():
    assert giro(pontos(44.0)) == pytest.approx(-0.4)

def test_olhos_colados_nao_divide_por_zero():
    assert np.isfinite(giro(pontos(61.0, olho_a=(60.0, 50.0), olho_b=(60.0, 50.0))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd facegym-biometria && pytest tests/test_liveness.py -v`
Expected: FAIL com `ModuleNotFoundError: No module named 'app.liveness'`

- [ ] **Step 3: Write minimal implementation**

`facegym-biometria/app/liveness.py`:
```python
import numpy as np

def giro(kps: np.ndarray) -> float:
    """Giro horizontal do rosto pelos 5 pontos do RetinaFace (olho esq., olho dir., nariz, boca esq.,
    boca dir., na ordem da imagem). 0 = de frente; positivo = a pessoa virou para a esquerda dela.

    A foto chega sem espelhamento: a esquerda da pessoa fica à direita da imagem, então virar
    para a esquerda leva o nariz para x maior.
    """
    olho_a, olho_b, nariz = kps[0], kps[1], kps[2]
    meio_x = (olho_a[0] + olho_b[0]) / 2
    distancia = max(abs(float(olho_b[0] - olho_a[0])), 1.0)  # perfil total: olhos quase colados
    return float((nariz[0] - meio_x) / distancia)
```

Em `facegym-biometria/app/embedder.py`, troque o topo e o método `embed` por:
```python
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol
import numpy as np

@dataclass(frozen=True)
class Rosto:
    embedding: np.ndarray  # 512 floats, normalizado
    kps: np.ndarray        # 5 pontos (x, y) na ordem da imagem

class Embedder(Protocol):
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None: ...
    def analyze(self, image_bgr: np.ndarray) -> Rosto | None: ...
```
e, na classe `InsightFaceEmbedder`, substitua `embed` por:
```python
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        rosto = self.analyze(image_bgr)
        return rosto.embedding if rosto else None

    def analyze(self, image_bgr: np.ndarray) -> Rosto | None:
        from insightface.app.common import Face

        bboxes, kpss = self._det.detect(image_bgr, max_num=0, metric="default")
        if bboxes.shape[0] == 0:
            return None
        areas = (bboxes[:, 2] - bboxes[:, 0]) * (bboxes[:, 3] - bboxes[:, 1])
        i = int(np.argmax(areas))
        face = Face(bbox=bboxes[i, :4], kps=kpss[i], det_score=bboxes[i, 4])
        self._rec.get(image_bgr, face)
        return Rosto(face.normed_embedding.astype(np.float32), np.asarray(kpss[i], dtype=np.float32))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd facegym-biometria && pytest -v`
Expected: PASS (os testes antigos continuam passando; `FakeEmbedder` ainda não tem `analyze`, o que só importa na Task 2)

- [ ] **Step 5: Commit**

```bash
git add facegym-biometria/app/liveness.py facegym-biometria/app/embedder.py facegym-biometria/tests/test_liveness.py
git commit -m "feat(biometria): giro do rosto a partir dos 5 pontos do detector"
```

---

### Task 2: Endpoint `/faces/identify-liveness`

**Files:**
- Modify: `facegym-biometria/app/api.py`, `facegym-biometria/tests/conftest.py`
- Test: `facegym-biometria/tests/test_api.py`, `facegym-biometria/tests/test_liveness_real.py`

**Interfaces:**
- Consumes: `giro`, `Rosto`, `Embedder.analyze` (Task 1).
- Produces: `POST /faces/identify-liveness` (multipart `image`, `turned`) → `{"alunoId": str|None, "score": float|None, "giroFrente": float|None, "giroVirada": float|None, "similaridade": float|None}`.

- [ ] **Step 1: Estender o embedder falso**

Em `facegym-biometria/tests/conftest.py`, substitua `FakeEmbedder`, as cores e a fixture `client` por:
```python
from app.embedder import Rosto

def kps_com_giro(g: float) -> np.ndarray:
    """Pontos cujo giro calculado é `g` (olhos a 40 px, nariz deslocado g*40 do meio)."""
    return np.array([(40, 50), (80, 50), (60 + g * 40, 70), (45, 90), (75, 90)], dtype=np.float32)

class FakeEmbedder:
    """Decide vetor e giro pela cor do pixel (0,0) da imagem BGR."""
    def __init__(self, by_rgb: dict[tuple[int, int, int], tuple[np.ndarray, float] | None]):
        self.by_rgb = by_rgb

    def analyze(self, image_bgr: np.ndarray) -> Rosto | None:
        b, g, r = (int(x) for x in image_bgr[0, 0])
        found = self.by_rgb.get((r, g, b))
        return Rosto(found[0], kps_com_giro(found[1])) if found else None

    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        rosto = self.analyze(image_bgr)
        return rosto.embedding if rosto else None

RED, GREEN, BLUE = (255, 0, 0), (0, 255, 0), (0, 0, 255)
ORANGE = (255, 128, 0)  # mesma pessoa de RED, virada para a esquerda dela

@pytest.fixture
def client(repo):
    embedder = FakeEmbedder({RED: (vec(0), 0.0), ORANGE: (vec(0), 0.4), GREEN: (vec(1), 0.0), BLUE: None})
    return TestClient(create_app(embedder, repo, KEY))
```

- [ ] **Step 2: Write the failing tests**

Acrescente ao fim de `facegym-biometria/tests/test_api.py`:
```python
from tests.conftest import ORANGE

def pair(frente, virada):
    return {"image": ("f.png", png(frente), "image/png"), "turned": ("v.png", png(virada), "image/png")}

def test_liveness_mede_giro_e_similaridade(client, auth):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files=upload(RED), headers=auth)
    r = client.post("/faces/identify-liveness", files=pair(RED, ORANGE), headers=auth)
    assert r.status_code == 200
    assert r.json() == {"alunoId": str(aluno), "score": 1.0, "giroFrente": 0.0, "giroVirada": 0.4, "similaridade": 1.0}

def test_liveness_pessoas_diferentes_tem_similaridade_zero(client, auth):
    r = client.post("/faces/identify-liveness", files=pair(RED, GREEN), headers=auth)
    assert r.json()["similaridade"] == 0.0

def test_liveness_sem_rosto_numa_das_fotos_devolve_tudo_nulo(client, auth):
    client.put(f"/faces/{uuid4()}", files=upload(RED), headers=auth)
    r = client.post("/faces/identify-liveness", files=pair(RED, BLUE), headers=auth)
    assert r.json() == {"alunoId": None, "score": None, "giroFrente": None, "giroVirada": None, "similaridade": None}

def test_liveness_sem_cadastro_mede_mas_nao_identifica(client, auth):
    r = client.post("/faces/identify-liveness", files=pair(RED, ORANGE), headers=auth)
    body = r.json()
    assert body["alunoId"] is None and body["score"] is None and body["giroVirada"] == 0.4

def test_liveness_exige_chave(client):
    assert client.post("/faces/identify-liveness", files=pair(RED, ORANGE)).status_code == 401

def test_liveness_aceita_duas_fotos_que_somam_mais_que_o_limite_de_uma(client, auth):
    # duas imagens de ~4 MB: o corpo passa de 5,5 MB, mas cada uma respeita os 5 MB
    grande = png(RED, size=(1200, 1200)) + b"\x00" * (4 * 1024 * 1024)
    files = {"image": ("f.png", grande, "image/png"), "turned": ("v.png", grande, "image/png")}
    assert client.post("/faces/identify-liveness", files=files, headers=auth).status_code == 200

def test_liveness_recusa_uma_foto_acima_de_5mb(client, auth):
    big = b"\x00" * (5 * 1024 * 1024 + 1)
    files = {"image": ("f.png", big, "image/png"), "turned": ("v.png", png(ORANGE), "image/png")}
    assert client.post("/faces/identify-liveness", files=files, headers=auth).status_code == 413
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd facegym-biometria && pytest tests/test_api.py -v -k liveness`
Expected: FAIL com status 404 (rota não existe) e 413 no teste das duas fotos grandes.

- [ ] **Step 4: Write minimal implementation**

Em `facegym-biometria/app/api.py`:

Imports:
```python
from app.embedder import Embedder, Rosto
from app.liveness import giro
```
Constantes (depois de `MAX_BODY`):
```python
LIVENESS_PATH = "/faces/identify-liveness"
# Duas imagens de até 5 MB cada, mais a margem do multipart.
MAX_BODY_LIVENESS = 2 * MAX_BYTES + 512 * 1024
```
No middleware, troque `if length is not None and length.isdigit() and int(length) > MAX_BODY:` por:
```python
            limite = MAX_BODY_LIVENESS if request.url.path == LIVENESS_PATH else MAX_BODY
            if length is not None and length.isdigit() and int(length) > limite:
```
Troque `read_embedding` por duas funções:
```python
    def read_image(image: UploadFile):
        data = image.file.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise HTTPException(status_code=413, detail="imagem maior que 5 MB")
        try:
            return decode_image(data)
        except InvalidImage:
            raise HTTPException(status_code=422, detail="imagem inválida")

    def read_embedding(image: UploadFile):
        return embedder.embed(read_image(image))
```
Novo endpoint (depois de `compare_demo`):
```python
    @app.post(LIVENESS_PATH, dependencies=[Depends(require_key)])
    def identify_liveness(image: UploadFile = File(...), turned: UploadFile = File(...)):
        # Só mede; quem decide se a prova de vida passou é a API.
        frente: Rosto | None = embedder.analyze(read_image(image))
        virada: Rosto | None = embedder.analyze(read_image(turned))
        if frente is None or virada is None:
            return {"alunoId": None, "score": None, "giroFrente": None, "giroVirada": None, "similaridade": None}
        match = repo.nearest(frente.embedding)
        return {
            "alunoId": str(match[0]) if match else None,
            "score": round(match[1], 4) if match else None,
            "giroFrente": round(giro(frente.kps), 4),
            "giroVirada": round(giro(virada.kps), 4),
            "similaridade": round(float(frente.embedding @ virada.embedding), 4),
        }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd facegym-biometria && pytest -v`
Expected: PASS (todos)

- [ ] **Step 6: Teste `slow` com o modelo real**

`facegym-biometria/tests/test_liveness_real.py`:
```python
from pathlib import Path
import numpy as np
import pytest
from app.images import decode_image
from app.liveness import giro

pytestmark = pytest.mark.slow

FOTOS = sorted((Path(__file__).parents[2] / "scripts" / "demo-fotos").glob("*.jpg"))

@pytest.fixture(scope="module")
def embedder():
    from app.embedder import InsightFaceEmbedder
    return InsightFaceEmbedder()

@pytest.mark.parametrize("foto", FOTOS, ids=lambda p: p.stem)
def test_retrato_frontal_mede_de_frente_e_espelho_inverte_o_sinal(embedder, foto):
    img = decode_image(foto.read_bytes())
    rosto = embedder.analyze(img)
    espelho = embedder.analyze(np.ascontiguousarray(img[:, ::-1]))
    assert rosto is not None and espelho is not None
    g, ge = giro(rosto.kps), giro(espelho.kps)
    print(f"\n{foto.stem}: giro={g:+.3f} espelhado={ge:+.3f}")
    assert abs(g) < 0.15
    assert ge == pytest.approx(-g, abs=0.05)
```
Run: `cd facegym-biometria && pytest -m slow tests/test_liveness_real.py -v -s`
Expected: PASS, com o giro de cada foto impresso. Se alguma foto da demo não for frontal (`|g| ≥ 0.15`), anote o valor impresso no relatório da task e não afrouxe o limiar aqui; a calibração (Task 9) decide.

- [ ] **Step 7: Commit**

```bash
git add facegym-biometria/app/api.py facegym-biometria/tests/conftest.py facegym-biometria/tests/test_api.py facegym-biometria/tests/test_liveness_real.py
git commit -m "feat(biometria): /faces/identify-liveness mede giro e similaridade das duas fotos"
```

---

### Task 3: Política de vivacidade no domínio

**Files:**
- Create: `domain/vivacidade/LadoDesafio.java`, `MedidasDeVivacidade.java`, `LimiaresDeVivacidade.java`, `PoliticaDeVivacidade.java`
- Test: `domain/vivacidade/PoliticaDeVivacidadeTest.java`

**Interfaces:**
- Produces:
  - `enum LadoDesafio { ESQUERDA, DIREITA }`
  - `record MedidasDeVivacidade(double giroFrente, double giroVirada, double similaridade)`
  - `record LimiaresDeVivacidade(double frente, double virada, double mesmaPessoa)`
  - `new PoliticaDeVivacidade(LimiaresDeVivacidade)`, `boolean aprova(LadoDesafio lado, MedidasDeVivacidade m)`

- [ ] **Step 1: Write the failing test**

`facegym-api/src/test/java/com/facegym/domain/vivacidade/PoliticaDeVivacidadeTest.java`:
```java
package com.facegym.domain.vivacidade;

import org.junit.jupiter.api.Test;

import static com.facegym.domain.vivacidade.LadoDesafio.DIREITA;
import static com.facegym.domain.vivacidade.LadoDesafio.ESQUERDA;
import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeVivacidadeTest {

    final PoliticaDeVivacidade politica = new PoliticaDeVivacidade(new LimiaresDeVivacidade(0.15, 0.25, 0.30));

    MedidasDeVivacidade m(double frente, double virada, double similaridade) {
        return new MedidasDeVivacidade(frente, virada, similaridade);
    }

    @Test
    void aprovaQuemVirouParaOLadoPedido() {
        assertThat(politica.aprova(ESQUERDA, m(0.02, 0.40, 0.70))).isTrue();
        assertThat(politica.aprova(DIREITA, m(-0.02, -0.40, 0.70))).isTrue();
    }

    @Test
    void reprovaLadoErrado() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, -0.40, 0.70))).isFalse();
        assertThat(politica.aprova(DIREITA, m(0.0, 0.40, 0.70))).isFalse();
    }

    @Test
    void limiteDaVirada() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.25, 0.70))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.2499, 0.70))).isFalse();
    }

    @Test
    void frenteTemQueEstarDeFrente() {
        assertThat(politica.aprova(ESQUERDA, m(0.1499, 0.40, 0.70))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.15, 0.40, 0.70))).isFalse();
        assertThat(politica.aprova(ESQUERDA, m(-0.15, 0.40, 0.70))).isFalse();
    }

    @Test
    void viradaEnviadaComoFrenteReprova() {
        // as duas fotos viradas: a "frente" não está de frente
        assertThat(politica.aprova(ESQUERDA, m(0.40, 0.40, 0.95))).isFalse();
    }

    @Test
    void pessoasDiferentesReprova() {
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.40, 0.30))).isTrue();
        assertThat(politica.aprova(ESQUERDA, m(0.0, 0.40, 0.2999))).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd facegym-api && ./mvnw -q test -Dtest=PoliticaDeVivacidadeTest`
Expected: FAIL de compilação (`package com.facegym.domain.vivacidade does not exist`)

- [ ] **Step 3: Write minimal implementation**

`domain/vivacidade/LadoDesafio.java`:
```java
package com.facegym.domain.vivacidade;

/** Para que lado a pessoa deve virar o rosto, do ponto de vista dela. */
public enum LadoDesafio { ESQUERDA, DIREITA }
```
`domain/vivacidade/MedidasDeVivacidade.java`:
```java
package com.facegym.domain.vivacidade;

/** Medidas da biometria: giro positivo = a pessoa virou para a esquerda dela. */
public record MedidasDeVivacidade(double giroFrente, double giroVirada, double similaridade) {
}
```
`domain/vivacidade/LimiaresDeVivacidade.java`:
```java
package com.facegym.domain.vivacidade;

/** `frente`: giro máximo (exclusivo) da foto de frente; `virada` e `mesmaPessoa`: mínimos (inclusivos). */
public record LimiaresDeVivacidade(double frente, double virada, double mesmaPessoa) {
}
```
`domain/vivacidade/PoliticaDeVivacidade.java`:
```java
package com.facegym.domain.vivacidade;

/**
 * Prova de vida por desafio: a primeira foto está de frente, a segunda virou para o lado sorteado
 * e as duas são da mesma pessoa. Uma foto parada não vira; um vídeo gravado não sabe o lado.
 */
public class PoliticaDeVivacidade {
    private final LimiaresDeVivacidade limiares;

    public PoliticaDeVivacidade(LimiaresDeVivacidade limiares) { this.limiares = limiares; }

    public boolean aprova(LadoDesafio lado, MedidasDeVivacidade m) {
        double giroNoLado = lado == LadoDesafio.ESQUERDA ? m.giroVirada() : -m.giroVirada();
        return Math.abs(m.giroFrente()) < limiares.frente()
                && giroNoLado >= limiares.virada()
                && m.similaridade() >= limiares.mesmaPessoa();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd facegym-api && ./mvnw -q test -Dtest='PoliticaDeVivacidadeTest,ArquiteturaTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src/main/java/com/facegym/domain/vivacidade facegym-api/src/test/java/com/facegym/domain/vivacidade
git commit -m "feat(api): política de vivacidade no domínio"
```

---

### Task 4: Portas de desafio e de reconhecimento com vivacidade; cliente HTTP

**Files:**
- Create: `application/port/DesafiosDeVivacidade.java`, `application/port/IdentificacaoComVivacidade.java`, `adapters/memoria/DesafiosDeVivacidadeEmMemoria.java`
- Modify: `application/port/ReconhecimentoFacial.java`, `adapters/biometria/BiometriaHttpClient.java`, test `application/Fakes.java`
- Test: `adapters/biometria/BiometriaHttpClientTest.java`, `adapters/memoria/DesafiosDeVivacidadeEmMemoriaTest.java`

**Interfaces:**
- Consumes: `LadoDesafio`, `MedidasDeVivacidade` (Task 3).
- Produces:
  - `interface DesafiosDeVivacidade { Desafio criar(Instant expiraEm); Optional<LadoDesafio> consumir(String token, Instant agora); record Desafio(String token, LadoDesafio lado) {} }`
  - `record IdentificacaoComVivacidade(UUID alunoId, Double score, Double giroFrente, Double giroVirada, Double similaridade)` com `Identificacao identificacao()` e `Optional<MedidasDeVivacidade> medidas()`
  - `ReconhecimentoFacial.identificarComVivacidade(byte[] frente, byte[] virada)`
  - Fakes: `Fakes.DesafiosFake` (campo `public LadoDesafio proximoLado = LadoDesafio.ESQUERDA`), `ReconhecimentoFake.proximaComVivacidade`, `Fakes.desafios`.

- [ ] **Step 1: Write the failing tests**

`facegym-api/src/test/java/com/facegym/adapters/memoria/DesafiosDeVivacidadeEmMemoriaTest.java`:
```java
package com.facegym.adapters.memoria;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DesafiosDeVivacidadeEmMemoriaTest {

    final DesafiosDeVivacidadeEmMemoria desafios = new DesafiosDeVivacidadeEmMemoria();
    final Instant agora = Instant.parse("2026-10-05T11:00:00Z");

    @Test
    void consumirDevolveOLadoUmaVezSo() {
        var d = desafios.criar(agora.plusSeconds(30));
        assertThat(desafios.consumir(d.token(), agora)).contains(d.lado());
        assertThat(desafios.consumir(d.token(), agora)).isEmpty();
    }

    @Test
    void expiradoOuDesconhecidoOuNuloNaoValem() {
        var d = desafios.criar(agora.plusSeconds(30));
        assertThat(desafios.consumir(d.token(), agora.plusSeconds(31))).isEmpty();
        assertThat(desafios.consumir("inventado", agora)).isEmpty();
        assertThat(desafios.consumir(null, agora)).isEmpty();
    }

    @Test
    void sorteiaOsDoisLados() {
        var lados = java.util.stream.IntStream.range(0, 64)
                .mapToObj(i -> desafios.criar(agora.plusSeconds(30)).lado()).distinct().count();
        assertThat(lados).isEqualTo(2);
    }
}
```

Acrescente a `BiometriaHttpClientTest.java` (imports: `com.facegym.application.port.IdentificacaoComVivacidade`, `com.facegym.domain.vivacidade.MedidasDeVivacidade`):
```java
    @Test
    void identificaComVivacidadeEnviandoAsDuasFotos() {
        UUID aluno = UUID.randomUUID();
        bio.stubFor(post("/faces/identify-liveness").withHeader("X-Internal-Key", equalTo(CHAVE))
                .withMultipartRequestBody(aMultipart().withName("image"))
                .withMultipartRequestBody(aMultipart().withName("turned"))
                .willReturn(okJson("{\"alunoId\":\"" + aluno + "\",\"score\":0.7,\"giroFrente\":0.01,\"giroVirada\":0.42,\"similaridade\":0.8}")));
        var r = client.identificarComVivacidade(FOTO, FOTO);
        assertThat(r).isEqualTo(new IdentificacaoComVivacidade(aluno, 0.7, 0.01, 0.42, 0.8));
        assertThat(r.medidas()).contains(new MedidasDeVivacidade(0.01, 0.42, 0.8));
    }

    @Test
    void semRostoNumaDasFotosNaoTemMedidas() {
        bio.stubFor(post("/faces/identify-liveness").willReturn(okJson(
                "{\"alunoId\":null,\"score\":null,\"giroFrente\":null,\"giroVirada\":null,\"similaridade\":null}")));
        var r = client.identificarComVivacidade(FOTO, FOTO);
        assertThat(r.medidas()).isEmpty();
        assertThat(r.identificacao().encontrou()).isFalse();
    }

    @Test
    void vivacidadeComBiometriaForaFicaIndisponivel() {
        bio.stubFor(post("/faces/identify-liveness").willReturn(serverError()));
        assertThatThrownBy(() -> client.identificarComVivacidade(FOTO, FOTO)).isInstanceOf(ReconhecimentoIndisponivel.class);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd facegym-api && ./mvnw -q test -Dtest='DesafiosDeVivacidadeEmMemoriaTest,BiometriaHttpClientTest'`
Expected: FAIL de compilação (classes e método não existem)

- [ ] **Step 3: Write minimal implementation**

`application/port/DesafiosDeVivacidade.java`:
```java
package com.facegym.application.port;

import com.facegym.domain.vivacidade.LadoDesafio;

import java.time.Instant;
import java.util.Optional;

public interface DesafiosDeVivacidade {
    /** Sorteia o lado e guarda até `expiraEm`. */
    Desafio criar(Instant expiraEm);
    /** Remove e devolve o lado; vazio se não existe ou expirou. Uso único. */
    Optional<LadoDesafio> consumir(String token, Instant agora);

    record Desafio(String token, LadoDesafio lado) {}
}
```
`application/port/IdentificacaoComVivacidade.java`:
```java
package com.facegym.application.port;

import com.facegym.domain.vivacidade.MedidasDeVivacidade;

import java.util.Optional;
import java.util.UUID;

/** Resposta da biometria para frente + virada; medidas nulas quando falta rosto numa das fotos. */
public record IdentificacaoComVivacidade(UUID alunoId, Double score, Double giroFrente, Double giroVirada,
                                         Double similaridade) {

    public Identificacao identificacao() { return new Identificacao(alunoId, score); }

    public Optional<MedidasDeVivacidade> medidas() {
        if (giroFrente == null || giroVirada == null || similaridade == null) return Optional.empty();
        return Optional.of(new MedidasDeVivacidade(giroFrente, giroVirada, similaridade));
    }
}
```
Em `application/port/ReconhecimentoFacial.java`, acrescente:
```java
    /** Identifica pela foto de frente e mede o giro das duas fotos e a similaridade entre elas. */
    IdentificacaoComVivacidade identificarComVivacidade(byte[] frente, byte[] virada);
```
`adapters/memoria/DesafiosDeVivacidadeEmMemoria.java`:
```java
package com.facegym.adapters.memoria;

import com.facegym.application.port.DesafiosDeVivacidade;
import com.facegym.domain.vivacidade.LadoDesafio;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Desafios vivem 30 s; memória basta (instância única). */
public class DesafiosDeVivacidadeEmMemoria implements DesafiosDeVivacidade {
    private record Entrada(LadoDesafio lado, Instant expiraEm) {}

    private final Map<String, Entrada> dados = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public Desafio criar(Instant expiraEm) {
        dados.values().removeIf(e -> e.expiraEm().isBefore(Instant.now().minusSeconds(300)));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        LadoDesafio lado = random.nextBoolean() ? LadoDesafio.ESQUERDA : LadoDesafio.DIREITA;
        dados.put(token, new Entrada(lado, expiraEm));
        return new Desafio(token, lado);
    }

    @Override
    public Optional<LadoDesafio> consumir(String token, Instant agora) {
        if (token == null) return Optional.empty();
        Entrada e = dados.remove(token);
        if (e == null || agora.isAfter(e.expiraEm())) return Optional.empty();
        return Optional.of(e.lado());
    }
}
```
Em `BiometriaHttpClient.java`:
- import `com.facegym.application.port.IdentificacaoComVivacidade;`
- ao lado de `IdentifyResponse`:
```java
    private record LivenessResponse(UUID alunoId, Double score, Double giroFrente, Double giroVirada, Double similaridade) {}
```
- depois de `compararDemo`:
```java
    @Override
    public IdentificacaoComVivacidade identificarComVivacidade(byte[] frente, byte[] virada) {
        return protegido(() -> {
            var parts = multipart(frente);
            parts.add("turned", new ByteArrayResource(virada) {
                @Override public String getFilename() { return "virada.jpg"; }
            });
            LivenessResponse r = http.post().uri("/faces/identify-liveness").contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts).retrieve().onStatus(BiometriaHttpClient::fotoInvalida, BiometriaHttpClient::lancarFotoInvalida)
                    .body(LivenessResponse.class);
            return r == null ? new IdentificacaoComVivacidade(null, null, null, null, null)
                    : new IdentificacaoComVivacidade(r.alunoId(), r.score(), r.giroFrente(), r.giroVirada(), r.similaridade());
        });
    }
```
Em `Fakes.java` (test), dentro de `ReconhecimentoFake`:
```java
        public IdentificacaoComVivacidade proximaComVivacidade = new IdentificacaoComVivacidade(null, null, null, null, null);
        public int chamadasComVivacidade = 0;

        public IdentificacaoComVivacidade identificarComVivacidade(byte[] frente, byte[] virada) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            chamadasComVivacidade++;
            return proximaComVivacidade;
        }
```
e, como nova classe interna de `Fakes` (import `com.facegym.domain.vivacidade.LadoDesafio`):
```java
    public static class DesafiosFake implements DesafiosDeVivacidade {
        public LadoDesafio proximoLado = LadoDesafio.ESQUERDA;
        private final Map<String, Map.Entry<LadoDesafio, Instant>> dados = new HashMap<>();
        private int seq = 0;
        public Desafio criar(Instant expiraEm) {
            String token = "desafio-" + (++seq);
            dados.put(token, Map.entry(proximoLado, expiraEm));
            return new Desafio(token, proximoLado);
        }
        public Optional<LadoDesafio> consumir(String token, Instant agora) {
            var e = token == null ? null : dados.remove(token);
            if (e == null || agora.isAfter(e.getValue())) return Optional.empty();
            return Optional.of(e.getKey());
        }
    }
```
com o campo `public final DesafiosFake desafios = new DesafiosFake();` junto aos outros fakes.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd facegym-api && ./mvnw -q test -Dtest='DesafiosDeVivacidadeEmMemoriaTest,BiometriaHttpClientTest,ArquiteturaTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): portas de desafio e de reconhecimento com vivacidade"
```

---

### Task 5: Coluna `vivacidade` no histórico de acessos

**Files:**
- Create: `facegym-api/src/main/resources/db/migration/V3__vivacidade.sql`
- Modify: `domain/Acesso.java`, `adapters/jdbc/AcessosJdbc.java`
- Test: `adapters/jdbc/JdbcAdaptersTest.java`

**Interfaces:**
- Produces: `record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score, Boolean vivacidade)`, mantendo o construtor de 7 argumentos (`vivacidade = null`). JSON do painel ganha `vivacidade`.

- [ ] **Step 1: Write the failing test**

Em `JdbcAdaptersTest.java`, acrescente:
```java
    @Test
    void acessoGuardaVivacidadeInclusiveNula() {
        Aluno a = Aluno.novo("Dora", Cpf.of("11144477735"), null);
        alunos.salvar(a);
        Instant t = Instant.parse("2026-10-06T11:00:00Z");
        acessos.registrar(new Acesso(UUID.randomUUID(), t, a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.FACIAL, 0.9, true));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(1), a.id(), ResultadoAcesso.NEGADO, "x", MeioIdentificacao.FACIAL, 0.9, false));
        acessos.registrar(new Acesso(UUID.randomUUID(), t.plusSeconds(2), a.id(), ResultadoAcesso.LIBERADO, null, MeioIdentificacao.CPF, null));

        assertThat(acessos.recentes(500)).filteredOn(x -> a.id().equals(x.alunoId()))
                .extracting(Acesso::vivacidade).containsExactly(null, false, true);
    }
```
(Se o CPF `11144477735` já for usado em outro teste da mesma classe, use `39053344705` com outro nome; confira com `grep -n 'Cpf.of' JdbcAdaptersTest.java`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd facegym-api && ./mvnw -q test -Dtest=JdbcAdaptersTest`
Expected: FAIL de compilação (construtor de 8 argumentos não existe). Docker Desktop precisa estar rodando.

- [ ] **Step 3: Write minimal implementation**

`V3__vivacidade.sql`:
```sql
-- null: não se aplica (CPF) ou acesso anterior à vivacidade
ALTER TABLE acesso ADD COLUMN vivacidade boolean;
```
`domain/Acesso.java`:
```java
package com.facegym.domain;

import java.time.Instant;
import java.util.UUID;

/** `vivacidade`: true com prova de vida, false sem ou reprovada, null quando não se aplica (CPF). */
public record Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                     MeioIdentificacao meio, Double score, Boolean vivacidade) {

    public Acesso(UUID id, Instant dataHora, UUID alunoId, ResultadoAcesso resultado, String motivo,
                  MeioIdentificacao meio, Double score) {
        this(id, dataHora, alunoId, resultado, motivo, meio, score, null);
    }
}
```
`AcessosJdbc.java`: no `MAPPER`, depois de `(Double) rs.getObject("score")`, acrescente `, (Boolean) rs.getObject("vivacidade")`; no `registrar`:
```java
        jdbc.sql("INSERT INTO acesso (id, data_hora, aluno_id, resultado, motivo, meio, score, vivacidade) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .param(a.id()).param(Timestamp.from(a.dataHora())).param(a.alunoId()).param(a.resultado().name())
                .param(a.motivo()).param(a.meio().name()).param(a.score()).param(a.vivacidade())
                .update();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd facegym-api && ./mvnw -q test -Dtest=JdbcAdaptersTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): histórico de acessos registra a prova de vida"
```

---

### Task 6: Caso de uso — check-in com desafio e modos

**Files:**
- Create: `application/ConfiguracaoDeVivacidade.java`
- Modify: `application/ResultadoCheckIn.java`, `application/RealizarCheckIn.java`, `application/port/CheckInsPendentes.java`, `adapters/memoria/CheckInsPendentesEmMemoria.java`, `adapters/config/UseCaseConfig.java` (só para compilar; config completa na Task 7), test `application/Fakes.java`
- Test: `application/RealizarCheckInVivacidadeTest.java`

**Interfaces:**
- Consumes: Tasks 3–5.
- Produces:
  - `record ConfiguracaoDeVivacidade(boolean obrigatoria, LimiaresDeVivacidade limiares)`
  - `ResultadoCheckIn.Liberado(String nome, boolean provaDeVida)` + construtor `Liberado(String nome)` (false)
  - `ResultadoCheckIn.ProvaDeVidaReprovada(String motivo)`
  - `RealizarCheckIn.novoDesafio(): DesafiosDeVivacidade.Desafio`
  - `RealizarCheckIn.porFotoComDesafio(byte[] frente, byte[] virada, String token): ResultadoCheckIn` (`virada` pode ser `null`)
  - Construtor: `RealizarCheckIn(ReconhecimentoFacial, Alunos, Planos, Matriculas, RegistroDeAcessos, CheckInsPendentes, DesafiosDeVivacidade, Relogio, Limiares, Duration antipassback, ConfiguracaoDeVivacidade)`
  - `CheckInsPendentes.criar(UUID alunoId, Double score, boolean provaDeVida, Instant expiraEm)`; `Pendente(UUID alunoId, Double score, boolean provaDeVida)`
  - Fakes: `checkIn()` (opcional, antipassback zero), `checkIn(Duration)`, `checkIn(Duration, boolean obrigatoria)`; constante `Fakes.LIMIARES_VIVACIDADE = new LimiaresDeVivacidade(0.15, 0.25, 0.30)`

- [ ] **Step 1: Write the failing test**

`facegym-api/src/test/java/com/facegym/application/RealizarCheckInVivacidadeTest.java`:
```java
package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.IdentificacaoComVivacidade;
import com.facegym.domain.*;
import com.facegym.domain.vivacidade.LadoDesafio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealizarCheckInVivacidadeTest {

    static final byte[] FRENTE = {1}, VIRADA = {2};
    static final String CPF_ANA = "529.982.247-25";
    Fakes f;
    Aluno ana;

    @BeforeEach
    void setUp() {
        f = new Fakes();
        Plano livre = new Plano(UUID.randomUUID(), "Livre", new BigDecimal("99.90"),
                EnumSet.allOf(DayOfWeek.class), LocalTime.MIN, LocalTime.MAX, null);
        f.planos.salvar(livre);
        ana = Aluno.novo("Ana", Cpf.of(CPF_ANA), null);
        f.alunos.salvar(ana);
        f.matriculas.salvar(new Matricula(UUID.randomUUID(), ana.id(), livre.id(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
    }

    IdentificacaoComVivacidade medida(double score, double frente, double virada, double similaridade) {
        return new IdentificacaoComVivacidade(ana.id(), score, frente, virada, similaridade);
    }

    Acesso ultimo() { return f.acessos.dados.getLast(); }

    @Test
    void desafioCumpridoLiberaComProvaDeVida() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        f.desafios.proximoLado = LadoDesafio.DIREITA;
        var d = checkIn.novoDesafio();
        assertThat(d.lado()).isEqualTo(LadoDesafio.DIREITA);
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.02, -0.41, 0.8);

        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new Liberado("Ana", true));
        assertThat(ultimo().vivacidade()).isTrue();
        assertThat(ultimo().meio()).isEqualTo(MeioIdentificacao.FACIAL);
    }

    @Test
    void desafioReprovadoNaoRevelaONomeERegistraSemAluno() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio(); // ESQUERDA
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.0, -0.41, 0.8); // virou para a direita

        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Prova de vida não confirmada"));
        assertThat(ultimo().resultado()).isEqualTo(ResultadoAcesso.NEGADO);
        assertThat(ultimo().alunoId()).isNull();
        assertThat(ultimo().vivacidade()).isFalse();
    }

    @Test
    void desafioReutilizadoEExpiradoNemChamamABiometria() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.proximaComVivacidade = medida(0.62, 0.0, 0.41, 0.8);
        checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token());

        var expirado = new ProvaDeVidaReprovada("Desafio expirado, tente de novo");
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(expirado);

        var outro = checkIn.novoDesafio();
        f.relogio.set(LocalDate.of(2026, 10, 5).atTime(8, 0, 31));
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, outro.token())).isEqualTo(expirado);
        assertThat(f.reconhecimento.chamadasComVivacidade).isEqualTo(1);
    }

    @Test
    void desafioSemFotoViradaConsomeOTokenEReprova() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        assertThat(checkIn.porFotoComDesafio(FRENTE, null, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Prova de vida não confirmada"));
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token()))
                .isEqualTo(new ProvaDeVidaReprovada("Desafio expirado, tente de novo"));
        assertThat(f.reconhecimento.chamadasComVivacidade).isZero();
    }

    @Test
    void semRostoNumaDasFotosNaoReconhece() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new NaoReconhecido());
    }

    @Test
    void biometriaForaNoDesafioCaiNoCpf() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.fora = true;
        assertThat(checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).isEqualTo(new BiometriaIndisponivel());
    }

    @Test
    void obrigatoriaRecusaFotoUnicaMasCpfContinua() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        assertThat(checkIn.porFoto(FRENTE)).isEqualTo(new ProvaDeVidaReprovada("Prova de vida obrigatória"));
        assertThat(ultimo().vivacidade()).isFalse();
        assertThat(checkIn.porCpf(CPF_ANA)).isEqualTo(new Liberado("Ana"));
        assertThat(ultimo().vivacidade()).isNull();
    }

    @Test
    void opcionalAceitaFotoUnicaSemProvaDeVida() {
        var checkIn = f.checkIn(Duration.ZERO, false);
        f.reconhecimento.proxima = new com.facegym.application.port.Identificacao(ana.id(), 0.62);
        assertThat(checkIn.porFoto(FRENTE)).isEqualTo(new Liberado("Ana", false));
        assertThat(ultimo().vivacidade()).isFalse();
    }

    @Test
    void scoreDeDuvidaComDesafioCumpridoGuardaAProvaDeVidaNaConfirmacaoPorCpf() {
        var checkIn = f.checkIn(Duration.ZERO, true);
        var d = checkIn.novoDesafio();
        f.reconhecimento.proximaComVivacidade = medida(0.30, 0.0, 0.41, 0.8);
        var token = ((ConfirmarCpf) checkIn.porFotoComDesafio(FRENTE, VIRADA, d.token())).token();
        assertThat(checkIn.confirmarCpf(token, CPF_ANA)).isEqualTo(new Liberado("Ana", true));
        assertThat(ultimo().vivacidade()).isTrue();
        assertThat(ultimo().meio()).isEqualTo(MeioIdentificacao.CPF);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd facegym-api && ./mvnw -q test -Dtest=RealizarCheckInVivacidadeTest`
Expected: FAIL de compilação (`novoDesafio`, `porFotoComDesafio`, `ProvaDeVidaReprovada`, `checkIn(Duration, boolean)` não existem)

- [ ] **Step 3: Write minimal implementation**

`application/ConfiguracaoDeVivacidade.java`:
```java
package com.facegym.application;

import com.facegym.domain.vivacidade.LimiaresDeVivacidade;

/** `obrigatoria = false` só na demo: aceita foto única (alunos fictícios) e registra sem prova de vida. */
public record ConfiguracaoDeVivacidade(boolean obrigatoria, LimiaresDeVivacidade limiares) {
}
```
`application/ResultadoCheckIn.java`:
```java
package com.facegym.application;

public sealed interface ResultadoCheckIn {
    record Liberado(String nome, boolean provaDeVida) implements ResultadoCheckIn {
        public Liberado(String nome) { this(nome, false); }
    }
    record Negado(String nome, String motivo) implements ResultadoCheckIn {}
    record ConfirmarCpf(String token) implements ResultadoCheckIn {}
    record NaoReconhecido() implements ResultadoCheckIn {}
    record BiometriaIndisponivel() implements ResultadoCheckIn {}
    /** Sem nome: se a prova de vida falhou, a identificação não é confiável. */
    record ProvaDeVidaReprovada(String motivo) implements ResultadoCheckIn {}
}
```
`application/port/CheckInsPendentes.java`: troque a assinatura e o record:
```java
    String criar(UUID alunoId, Double score, boolean provaDeVida, Instant expiraEm);
    ...
    record Pendente(UUID alunoId, Double score, boolean provaDeVida) {}
```
`CheckInsPendentesEmMemoria.criar`: assinatura `criar(UUID alunoId, Double score, boolean provaDeVida, Instant expiraEm)` e `new Pendente(alunoId, score, provaDeVida)`.

`application/RealizarCheckIn.java` inteiro:
```java
package com.facegym.application;

import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.*;
import com.facegym.domain.*;
import com.facegym.domain.vivacidade.LadoDesafio;
import com.facegym.domain.vivacidade.MedidasDeVivacidade;
import com.facegym.domain.vivacidade.PoliticaDeVivacidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class RealizarCheckIn {
    static final Duration VALIDADE_CONFIRMACAO = Duration.ofSeconds(60);
    static final Duration VALIDADE_DESAFIO = Duration.ofSeconds(30);
    static final String PROVA_OBRIGATORIA = "Prova de vida obrigatória";
    static final String DESAFIO_EXPIRADO = "Desafio expirado, tente de novo";
    static final String PROVA_NAO_CONFIRMADA = "Prova de vida não confirmada";

    private final ReconhecimentoFacial reconhecimento;
    private final Alunos alunos;
    private final Planos planos;
    private final Matriculas matriculas;
    private final RegistroDeAcessos acessos;
    private final CheckInsPendentes pendentes;
    private final DesafiosDeVivacidade desafios;
    private final Relogio relogio;
    private final Limiares limiares;
    private final PoliticaDeAcesso politica;
    private final boolean vivacidadeObrigatoria;
    private final PoliticaDeVivacidade politicaDeVivacidade;

    public RealizarCheckIn(ReconhecimentoFacial reconhecimento, Alunos alunos, Planos planos, Matriculas matriculas,
                           RegistroDeAcessos acessos, CheckInsPendentes pendentes, DesafiosDeVivacidade desafios,
                           Relogio relogio, Limiares limiares, Duration antipassback,
                           ConfiguracaoDeVivacidade vivacidade) {
        this.reconhecimento = reconhecimento;
        this.alunos = alunos;
        this.planos = planos;
        this.matriculas = matriculas;
        this.acessos = acessos;
        this.pendentes = pendentes;
        this.desafios = desafios;
        this.relogio = relogio;
        this.limiares = limiares;
        this.politica = new PoliticaDeAcesso(antipassback);
        this.vivacidadeObrigatoria = vivacidade.obrigatoria();
        this.politicaDeVivacidade = new PoliticaDeVivacidade(vivacidade.limiares());
    }

    public DesafiosDeVivacidade.Desafio novoDesafio() {
        return desafios.criar(relogio.agora().plus(VALIDADE_DESAFIO));
    }

    public ResultadoCheckIn porFoto(byte[] foto) {
        if (vivacidadeObrigatoria) {
            registrar(null, ResultadoAcesso.NEGADO, PROVA_OBRIGATORIA, MeioIdentificacao.FACIAL, null, false);
            return new ProvaDeVidaReprovada(PROVA_OBRIGATORIA);
        }
        Identificacao id;
        try {
            id = reconhecimento.identificar(foto);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null, false);
            return new BiometriaIndisponivel();
        }
        return identificado(id, false);
    }

    /** `virada` nula conta como desafio não cumprido; o token é consumido do mesmo jeito. */
    public ResultadoCheckIn porFotoComDesafio(byte[] frente, byte[] virada, String token) {
        Optional<LadoDesafio> lado = desafios.consumir(token, relogio.agora());
        if (lado.isEmpty()) return reprovar(DESAFIO_EXPIRADO, null);
        if (virada == null) return reprovar(PROVA_NAO_CONFIRMADA, null);
        IdentificacaoComVivacidade r;
        try {
            r = reconhecimento.identificarComVivacidade(frente, virada);
        } catch (ReconhecimentoIndisponivel e) {
            registrar(null, ResultadoAcesso.NEGADO, "Reconhecimento facial indisponível", MeioIdentificacao.FACIAL, null, false);
            return new BiometriaIndisponivel();
        }
        Optional<MedidasDeVivacidade> medidas = r.medidas();
        if (medidas.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, null, false);
            return new NaoReconhecido();
        }
        if (!politicaDeVivacidade.aprova(lado.get(), medidas.get())) return reprovar(PROVA_NAO_CONFIRMADA, r.score());
        return identificado(r.identificacao(), true);
    }

    public ResultadoCheckIn confirmarCpf(String token, String cpf) {
        Cpf informado = Cpf.of(cpf);
        Optional<CheckInsPendentes.Pendente> pendente = pendentes.consumir(token, relogio.agora());
        if (pendente.isEmpty()) return new NaoReconhecido();
        Optional<Aluno> aluno = alunos.porId(pendente.get().alunoId());
        if (aluno.isEmpty() || !aluno.get().cpf().equals(informado)) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não confere com o rosto", MeioIdentificacao.CPF,
                    pendente.get().score(), pendente.get().provaDeVida());
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, pendente.get().score(), pendente.get().provaDeVida());
    }

    public ResultadoCheckIn porCpf(String cpf) {
        Optional<Aluno> aluno = alunos.porCpf(Cpf.of(cpf));
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "CPF não cadastrado", MeioIdentificacao.CPF, null, null);
            return new NaoReconhecido();
        }
        return decidir(aluno.get(), MeioIdentificacao.CPF, null, null);
    }

    /** Câmera de visitantes: só diz de quem é o rosto, sem registrar acesso. */
    public Optional<String> demo(byte[] foto) {
        Identificacao id = reconhecimento.compararDemo(foto);
        if (!id.encontrou() || id.score() < limiares.aceite()) return Optional.empty();
        return alunos.porId(id.alunoId()).map(Aluno::nome);
    }

    private ResultadoCheckIn identificado(Identificacao id, boolean provaDeVida) {
        if (!id.encontrou() || id.score() < limiares.duvida()) {
            registrar(null, ResultadoAcesso.NEGADO, "Rosto não reconhecido", MeioIdentificacao.FACIAL, id.score(), provaDeVida);
            return new NaoReconhecido();
        }
        Optional<Aluno> aluno = alunos.porId(id.alunoId());
        if (aluno.isEmpty()) {
            registrar(null, ResultadoAcesso.NEGADO, "Biometria sem aluno cadastrado", MeioIdentificacao.FACIAL, id.score(), provaDeVida);
            return new NaoReconhecido();
        }
        if (id.score() < limiares.aceite()) {
            return new ConfirmarCpf(pendentes.criar(id.alunoId(), id.score(), provaDeVida,
                    relogio.agora().plus(VALIDADE_CONFIRMACAO)));
        }
        return decidir(aluno.get(), MeioIdentificacao.FACIAL, id.score(), provaDeVida);
    }

    /** Sem aluno no registro: a identificação de quem falhou a prova de vida não é confiável. */
    private ResultadoCheckIn reprovar(String motivo, Double score) {
        registrar(null, ResultadoAcesso.NEGADO, motivo, MeioIdentificacao.FACIAL, score, false);
        return new ProvaDeVidaReprovada(motivo);
    }

    private ResultadoCheckIn decidir(Aluno aluno, MeioIdentificacao meio, Double score, Boolean provaDeVida) {
        Instant agora = relogio.agora();
        LocalDateTime local = LocalDateTime.ofInstant(agora, relogio.fuso());
        Optional<Matricula> matricula = matriculas.vigente(aluno.id(), local.toLocalDate());
        Optional<Plano> plano = matricula.flatMap(m -> planos.porId(m.planoId()));
        Instant inicioSemana = Semana.inicio(local.toLocalDate()).atStartOfDay(relogio.fuso()).toInstant();
        long liberados = acessos.liberadosDesde(aluno.id(), inicioSemana);
        Optional<LocalDateTime> ultimaEntrada = acessos.ultimoLiberado(aluno.id())
                .map(i -> LocalDateTime.ofInstant(i, relogio.fuso()));

        Decisao decisao = politica.avaliar(
                new ContextoDeAcesso(aluno, matricula, plano, local, liberados, ultimaEntrada));
        if (decisao instanceof Decisao.Nega nega) {
            registrar(aluno.id(), ResultadoAcesso.NEGADO, nega.motivo(), meio, score, provaDeVida);
            return new Negado(aluno.nome(), nega.motivo());
        }
        registrar(aluno.id(), ResultadoAcesso.LIBERADO, null, meio, score, provaDeVida);
        return new Liberado(aluno.nome(), Boolean.TRUE.equals(provaDeVida));
    }

    private void registrar(UUID alunoId, ResultadoAcesso resultado, String motivo, MeioIdentificacao meio, Double score,
                           Boolean vivacidade) {
        acessos.registrar(new Acesso(UUID.randomUUID(), relogio.agora(), alunoId, resultado, motivo, meio, score, vivacidade));
    }
}
```
`Fakes.java` (test): substitua os métodos `checkIn`:
```java
    public static final LimiaresDeVivacidade LIMIARES_VIVACIDADE = new LimiaresDeVivacidade(0.15, 0.25, 0.30);

    /** Modo opcional: os testes antigos usam foto única. */
    public RealizarCheckIn checkIn() {
        return checkIn(Duration.ZERO);
    }

    public RealizarCheckIn checkIn(Duration antipassback) {
        return checkIn(antipassback, false);
    }

    public RealizarCheckIn checkIn(Duration antipassback, boolean vivacidadeObrigatoria) {
        return new RealizarCheckIn(reconhecimento, alunos, planos, matriculas, acessos, pendentes, desafios, relogio,
                new Limiares(0.41, 0.18), antipassback,
                new ConfiguracaoDeVivacidade(vivacidadeObrigatoria, LIMIARES_VIVACIDADE));
    }
```
(import `com.facegym.domain.vivacidade.LimiaresDeVivacidade`.)

`UseCaseConfig.java`, só para compilar (a Task 7 troca por configuração): no bean `realizarCheckIn`, acrescente o parâmetro `DesafiosDeVivacidade desafios` e troque o `return` por
```java
        return new RealizarCheckIn(r, a, p, m, ac, pend, desafios, rel, new Limiares(aceite, duvida), antipassback,
                new ConfiguracaoDeVivacidade(false, new LimiaresDeVivacidade(0.15, 0.25, 0.30)));
```
e acrescente o bean:
```java
    @Bean
    DesafiosDeVivacidade desafiosDeVivacidade() { return new DesafiosDeVivacidadeEmMemoria(); }
```
(imports `com.facegym.adapters.memoria.DesafiosDeVivacidadeEmMemoria`, `com.facegym.domain.vivacidade.LimiaresDeVivacidade`.)

`CheckInController.Resposta.de`: o `switch` precisa cobrir o novo record para compilar; acrescente
```java
                case ProvaDeVidaReprovada p -> new Resposta("PROVA_DE_VIDA_REPROVADA", null, p.motivo(), null);
```
(o campo `vivacidade` da resposta entra na Task 7).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd facegym-api && ./mvnw -q test -Dtest='RealizarCheckIn*Test,ArquiteturaTest'`
Expected: PASS (inclusive `RealizarCheckInTest` antigo, que usa o modo opcional)

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src
git commit -m "feat(api): check-in com desafio de vivacidade e modos obrigatória/opcional"
```

---

### Task 7: HTTP, configuração e deploy

**Files:**
- Modify: `adapters/web/CheckInController.java`, `adapters/config/UseCaseConfig.java`, `facegym-api/src/main/resources/application.yml`, `docker-compose.yml`, `render.yaml`
- Test: `FluxoCompletoIT.java`

**Interfaces:**
- Consumes: `RealizarCheckIn.novoDesafio`, `porFotoComDesafio`, `ProvaDeVidaReprovada`, `Liberado.provaDeVida` (Task 6).
- Produces: `POST /api/v1/check-ins/desafios` → `{"token","lado"}`; `POST /api/v1/check-ins` aceita `fotoVirada` e `desafio` opcionais; resposta ganha `vivacidade: true` (omitida quando falsa); status `PROVA_DE_VIDA_REPROVADA` sem `nome`.

- [ ] **Step 1: Write the failing tests**

Em `FluxoCompletoIT.props`, acrescente `r.add("facegym.vivacidade.modo", () -> "opcional");` (o teste antigo usa foto única). Acrescente os testes:
```java
    @Test
    void desafioDeVivacidadeDePontaAPonta() throws Exception {
        String auth = token();
        String plano = id(mvc.perform(post("/api/v1/planos").header("Authorization", auth).contentType("application/json")
                        .content("{\"nome\":\"Livre\",\"preco\":99.9,\"dias\":[\"MONDAY\"],\"inicio\":\"06:00\",\"fim\":\"22:00\",\"acessosPorSemana\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String aluno = id(mvc.perform(post("/api/v1/alunos").header("Authorization", auth).contentType("application/json")
                        .content("{\"nome\":\"Bia\",\"cpf\":\"390.533.447-05\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/matriculas").header("Authorization", auth).contentType("application/json")
                        .content("{\"alunoId\":\"" + aluno + "\",\"planoId\":\"" + plano + "\",\"inicio\":\"2026-10-01\",\"vencimento\":\"2026-10-31\"}"))
                .andExpect(status().isCreated());

        String desafio = mvc.perform(post("/api/v1/check-ins/desafios"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lado").isString())
                .andReturn().getResponse().getContentAsString();
        String tokenDesafio = desafio.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        boolean esquerda = desafio.contains("ESQUERDA");

        bio.stubFor(WireMock.post("/faces/identify-liveness").willReturn(WireMock.okJson(
                "{\"alunoId\":\"" + aluno + "\",\"score\":0.7,\"giroFrente\":0.0,\"giroVirada\":" + (esquerda ? "0.4" : "-0.4")
                        + ",\"similaridade\":0.8}")));
        var frente = new MockMultipartFile("foto", "f.jpg", "image/jpeg", new byte[]{1});
        var virada = new MockMultipartFile("fotoVirada", "v.jpg", "image/jpeg", new byte[]{2});
        mvc.perform(multipart("/api/v1/check-ins").file(frente).file(virada).param("desafio", tokenDesafio))
                .andExpect(jsonPath("$.status").value("LIBERADO"))
                .andExpect(jsonPath("$.nome").value("Bia"))
                .andExpect(jsonPath("$.vivacidade").value(true));

        // mesmo token de novo: reprovado, sem nome na resposta
        mvc.perform(multipart("/api/v1/check-ins").file(frente).file(virada).param("desafio", tokenDesafio))
                .andExpect(jsonPath("$.status").value("PROVA_DE_VIDA_REPROVADA"))
                .andExpect(jsonPath("$.motivo").value("Desafio expirado, tente de novo"))
                .andExpect(jsonPath("$.nome").doesNotExist());

        mvc.perform(get("/api/v1/acessos").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.alunoId == '" + aluno + "')].vivacidade").value(org.hamcrest.Matchers.hasItem(true)));
    }
```

(O banco do `FluxoCompletoIT` é compartilhado entre os testes: confira com `grep -n 'cpf' FluxoCompletoIT.java` que `390.533.447-05` não é usado em outro teste; se for, escolha outro CPF válido.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd facegym-api && ./mvnw -q verify -Dit.test=FluxoCompletoIT -Dtest=FluxoCompletoIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`/check-ins/desafios` responde 404/405; `vivacidade` ausente). Docker precisa estar rodando.

- [ ] **Step 3: Write minimal implementation**

`CheckInController.java`:
- `Resposta` ganha o campo `Boolean vivacidade` no fim, e o `switch` fica:
```java
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resposta(String status, String nome, String motivo, String token, Boolean vivacidade) {
        static Resposta de(ResultadoCheckIn r) {
            return switch (r) {
                case Liberado l -> new Resposta("LIBERADO", l.nome(), null, null, l.provaDeVida() ? true : null);
                case Negado n -> new Resposta("NEGADO", n.nome(), n.motivo(), null, null);
                case ConfirmarCpf c -> new Resposta("CONFIRMAR_CPF", null, null, c.token(), null);
                case NaoReconhecido x -> new Resposta("NAO_RECONHECIDO", null, null, null, null);
                case BiometriaIndisponivel x -> new Resposta("BIOMETRIA_INDISPONIVEL", null, null, null, null);
                case ProvaDeVidaReprovada p -> new Resposta("PROVA_DE_VIDA_REPROVADA", null, p.motivo(), null, null);
            };
        }
    }
```
- troque o `porFoto` e acrescente o desafio:
```java
    @PostMapping("/check-ins/desafios")
    public Map<String, String> desafio() {
        var d = checkIn.novoDesafio();
        return Map.of("token", d.token(), "lado", d.lado().name());
    }

    @PostMapping("/check-ins")
    public Resposta porFoto(@RequestParam("foto") MultipartFile foto,
                            @RequestParam(value = "fotoVirada", required = false) MultipartFile virada,
                            @RequestParam(value = "desafio", required = false) String desafio) throws IOException {
        if (desafio != null || virada != null) {
            return Resposta.de(checkIn.porFotoComDesafio(foto.getBytes(), virada == null ? null : virada.getBytes(), desafio));
        }
        return Resposta.de(checkIn.porFoto(foto.getBytes()));
    }
```
`UseCaseConfig.realizarCheckIn`: troque os parâmetros provisórios da Task 6 por configuração:
```java
    @Bean
    RealizarCheckIn realizarCheckIn(ReconhecimentoFacial r, Alunos a, Planos p, Matriculas m, RegistroDeAcessos ac,
                                    CheckInsPendentes pend, DesafiosDeVivacidade desafios, Relogio rel,
                                    @Value("${facegym.limiares.aceite}") double aceite,
                                    @Value("${facegym.limiares.duvida}") double duvida,
                                    @Value("${facegym.antipassback}") Duration antipassback,
                                    @Value("${facegym.vivacidade.modo}") String modo,
                                    @Value("${facegym.vivacidade.frente}") double frente,
                                    @Value("${facegym.vivacidade.virada}") double virada,
                                    @Value("${facegym.vivacidade.mesma-pessoa}") double mesmaPessoa) {
        boolean obrigatoria = switch (modo) {
            case "obrigatoria" -> true;
            case "opcional" -> false;
            default -> throw new IllegalStateException("VIVACIDADE deve ser obrigatoria ou opcional, veio: " + modo);
        };
        return new RealizarCheckIn(r, a, p, m, ac, pend, desafios, rel, new Limiares(aceite, duvida), antipassback,
                new ConfiguracaoDeVivacidade(obrigatoria, new LimiaresDeVivacidade(frente, virada, mesmaPessoa)));
    }
```
`application.yml`: depois de `antipassback`:
```yaml
  vivacidade:
    modo: ${VIVACIDADE:obrigatoria}  # opcional: aceita foto única (demo com alunos fictícios)
    frente: ${VIVACIDADE_FRENTE:0.15}
    virada: ${VIVACIDADE_VIRADA:0.25}
    mesma-pessoa: ${VIVACIDADE_MESMA_PESSOA:0.30}
```
e, no multipart, `max-request-size: 11MB` (duas fotos de até 5 MB).

`docker-compose.yml`, no `environment` do serviço `api`, acrescente `VIVACIDADE: opcional  # alunos fictícios da demo usam foto única`.

`render.yaml`, nas `envVars` do `facegym-api`:
```yaml
      - key: VIVACIDADE
        value: opcional  # alunos fictícios da demo usam foto única
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd facegym-api && ./mvnw -q verify`
Expected: PASS (suíte inteira, com Docker rodando)

- [ ] **Step 5: Commit**

```bash
git add facegym-api/src docker-compose.yml render.yaml
git commit -m "feat(api): endpoints de desafio de vivacidade e modo configurável"
```

---

### Task 8: Totem e painel

**Files:**
- Create: `facegym-web/src/desafio.ts`, `facegym-web/src/desafio.test.ts`
- Modify: `facegym-web/src/resultado.ts`, `resultado.test.ts`, `Camera.tsx`, `Catraca.tsx`, `Totem.tsx`, `painel/Painel.tsx`

**Interfaces:**
- Consumes: contratos HTTP da Task 7.
- Produces: `type Lado`, `type Desafio`, `type FotosComDesafio`, `instrucao(lado)`, `SEGUNDOS_DESAFIO` em `desafio.ts`; `descrever(r, demo?)` com `selo?`; `EstadoCatraca` com `{ fase: 'desafio'; lado }` e `demo?` na resposta.

- [ ] **Step 1: Write the failing tests**

`facegym-web/src/desafio.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { instrucao } from './desafio'

describe('instrucao', () => {
  it('fala do ponto de vista da pessoa, com a seta do lado que ela vê na tela espelhada', () => {
    expect(instrucao('ESQUERDA')).toEqual({ texto: 'Vire o rosto para a esquerda', seta: '←' })
    expect(instrucao('DIREITA')).toEqual({ texto: 'Vire o rosto para a direita', seta: '→' })
  })
})
```
Acrescente em `resultado.test.ts`, dentro de `describe('descrever', ...)`:
```ts
  it('liberado com prova de vida ganha selo', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana', vivacidade: true }).selo).toBe('✓ Prova de vida')
  })
  it('aluno fictício liberado sem prova de vida ganha selo de demo; CPF não ganha selo', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' }, true).selo).toBe('Sem prova de vida (demo)')
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' }).selo).toBeUndefined()
  })
  it('prova de vida reprovada pede nova tentativa, sem nome e sem CPF', () => {
    expect(descrever({ status: 'PROVA_DE_VIDA_REPROVADA', motivo: 'Prova de vida não confirmada' }))
      .toEqual({ titulo: 'Não deu para confirmar', detalhe: 'Prova de vida não confirmada — tente de novo.', tom: 'aviso', pedeCpf: false })
  })
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd facegym-web && npm test -- --run`
Expected: FAIL (`./desafio` não existe; `selo` indefinido; status desconhecido)

- [ ] **Step 3: Write minimal implementation**

`facegym-web/src/desafio.ts`:
```ts
export type Lado = 'ESQUERDA' | 'DIREITA'
export type Desafio = { token: string; lado: Lado }
export type FotosComDesafio = { frente: Blob; virada: Blob; desafio: Desafio }

export const SEGUNDOS_DESAFIO = 3

/** O preview é espelhado: a esquerda da pessoa aparece à esquerda da tela, então a seta acompanha. */
export function instrucao(lado: Lado): { texto: string; seta: string } {
  return lado === 'ESQUERDA'
    ? { texto: 'Vire o rosto para a esquerda', seta: '←' }
    : { texto: 'Vire o rosto para a direita', seta: '→' }
}
```
`resultado.ts`: tipo e função:
```ts
export type RespostaCheckIn = {
  status: 'LIBERADO' | 'NEGADO' | 'CONFIRMAR_CPF' | 'NAO_RECONHECIDO' | 'BIOMETRIA_INDISPONIVEL' | 'PROVA_DE_VIDA_REPROVADA'
  nome?: string
  motivo?: string
  token?: string
  vivacidade?: boolean
}

export type Descricao = { titulo: string; detalhe?: string; tom: 'ok' | 'erro' | 'aviso'; pedeCpf: boolean; selo?: string }

/** `demo`: check-in de aluno fictício, que usa foto única. */
export function descrever(r: RespostaCheckIn, demo = false): Descricao {
  switch (r.status) {
    case 'LIBERADO': {
      const selo = r.vivacidade ? '✓ Prova de vida' : demo ? 'Sem prova de vida (demo)' : undefined
      return { titulo: `Bem-vinda(o), ${r.nome}!`, tom: 'ok', pedeCpf: false, ...(selo && { selo }) }
    }
    case 'NEGADO':
      return { titulo: `Acesso negado, ${r.nome}`, detalhe: r.motivo, tom: 'erro', pedeCpf: false }
    case 'CONFIRMAR_CPF':
      return { titulo: 'Quase lá', detalhe: 'Confirme seu CPF para entrar.', tom: 'aviso', pedeCpf: true }
    case 'NAO_RECONHECIDO':
      return { titulo: 'Rosto não reconhecido', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'BIOMETRIA_INDISPONIVEL':
      return { titulo: 'Reconhecimento indisponível', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'PROVA_DE_VIDA_REPROVADA':
      return { titulo: 'Não deu para confirmar', detalhe: `${r.motivo ?? 'Prova de vida não confirmada'} — tente de novo.`, tom: 'aviso', pedeCpf: false }
  }
}
```
`Camera.tsx`: `onFoto` vira opcional e entra o modo com desafio. Substitua `capturar` e acrescente o estado e o overlay:
```tsx
import { SEGUNDOS_DESAFIO, instrucao, type Desafio, type FotosComDesafio, type Lado } from './desafio'

export type ComDesafio = {
  pedir: () => Promise<Desafio>
  onDesafio: (d: Desafio) => void
  onFotos: (f: FotosComDesafio) => void
  onErro: (e: unknown) => void
}

const esperar = (ms: number) => new Promise((ok) => setTimeout(ok, ms))

export function Camera({ onFoto, comDesafio, rotulo, desabilitado, terminal }: {
  onFoto?: (foto: Blob) => void
  comDesafio?: ComDesafio
  rotulo: string
  desabilitado?: boolean
  terminal?: boolean
}) {
  const video = useRef<HTMLVideoElement>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [contagem, setContagem] = useState<{ lado: Lado; segundos: number } | null>(null)

  // (useEffect da câmera inalterado)

  /** JPEG do quadro atual, sem o espelhamento do preview. */
  function capturar(): Promise<Blob | null> {
    const v = video.current
    if (!v || !v.videoWidth) return Promise.resolve(null)
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth; canvas.height = v.videoHeight
    canvas.getContext('2d')!.drawImage(v, 0, 0)
    return new Promise((ok) => canvas.toBlob(ok, 'image/jpeg', 0.9))
  }

  async function clicar() {
    if (!comDesafio) {
      const foto = await capturar()
      if (foto) onFoto?.(foto)
      return
    }
    try {
      const desafio = await comDesafio.pedir()
      const frente = await capturar()
      if (!frente) return
      comDesafio.onDesafio(desafio)
      for (let s = SEGUNDOS_DESAFIO; s > 0; s--) {
        setContagem({ lado: desafio.lado, segundos: s })
        await esperar(1000)
      }
      const virada = await capturar()
      setContagem(null)
      if (virada) comDesafio.onFotos({ frente, virada, desafio })
    } catch (e) {
      setContagem(null)
      comDesafio.onErro(e)
    }
  }
```
Troque `onClick={capturar}` por `onClick={clicar}` e `disabled={desabilitado}` por `disabled={desabilitado || !!contagem}` nos dois botões. No ramo `terminal`, logo depois do `<video>`, acrescente o overlay:
```tsx
        {contagem && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/35 font-visor text-white" aria-live="assertive">
            <span className="text-7xl leading-none">{instrucao(contagem.lado).seta}</span>
            <span className="px-6 text-center text-xl font-semibold">{instrucao(contagem.lado).texto}</span>
            <span className="text-4xl tabular-nums">{contagem.segundos}</span>
          </div>
        )}
```
`Catraca.tsx`:
- import `import { instrucao, type Lado } from './desafio'`
- `EstadoCatraca` ganha `| { fase: 'desafio'; lado: Lado }` e a resposta vira `{ fase: 'resposta'; resposta: RespostaCheckIn; demo?: boolean }`
- em `Catraca`, `const d = estado.fase === 'resposta' ? descrever(estado.resposta, estado.demo) : null`
- em `Visor`, antes do `if (estado.fase === 'erro')`:
```tsx
  if (estado.fase === 'desafio') {
    const i = instrucao(estado.lado)
    return <Mensagem titulo={`${i.texto} ${i.seta}`} detalhe="Segure até a foto." cor="text-sinal-aviso" />
  }
```
- no fim de `Visor`, `const d = descrever(estado.resposta, estado.demo)` e, logo depois do `<Mensagem …/>`:
```tsx
      {d.selo && <p className="-mt-2 pb-3 text-center text-xs font-semibold text-slate-300">{d.selo}</p>}
```
`Totem.tsx`:
```tsx
import type { Desafio, FotosComDesafio } from './desafio'
```
- `const ocupado = estado.fase === 'lendo' || estado.fase === 'iniciando' || estado.fase === 'desafio'`
- `executar` recebe `demo = false` e grava `setEstado({ fase: 'resposta', resposta: r, demo })`:
```tsx
  async function executar(acao: () => Promise<RespostaCheckIn>, demo = false) {
    setEstado({ fase: 'lendo' })
    try {
      const r = await acao()
      setToken(r.token)
      setEstado({ fase: 'resposta', resposta: r, demo })
    } catch (e) {
      setEstado({ fase: 'erro', mensagem: e instanceof ApiError ? e.message : 'Erro inesperado' })
    }
  }
```
- `checkInFoto` recebe `demo`:
```tsx
  const checkInFoto = (blob: Blob, demo = false) => executar(() => {
    const form = new FormData(); form.append('foto', blob, 'foto.jpg')
    return apiForm<RespostaCheckIn>('/check-ins', form)
  }, demo)

  const checkInComDesafio = ({ frente, virada, desafio }: FotosComDesafio) => executar(() => {
    const form = new FormData()
    form.append('foto', frente, 'frente.jpg')
    form.append('fotoVirada', virada, 'virada.jpg')
    form.append('desafio', desafio.token)
    return apiForm<RespostaCheckIn>('/check-ins', form)
  })

  const comDesafio = {
    pedir: () => apiJson<Desafio>('/check-ins/desafios', { method: 'POST' }),
    onDesafio: (d: Desafio) => setEstado({ fase: 'desafio', lado: d.lado }),
    onFotos: checkInComDesafio,
    onErro: (e: unknown) => setEstado({ fase: 'erro', mensagem: e instanceof ApiError ? e.message : 'Erro inesperado' }),
  }
  const reprovado = estado.fase === 'resposta' && estado.resposta.status === 'PROVA_DE_VIDA_REPROVADA'
```
- em `passarAluno`: `checkInFoto(await fotoDeUrl(`/demo/${slug}.jpg`), true)`
- na `tela`: `<Camera terminal rotulo={reprovado ? 'Tentar de novo' : 'Fazer check-in'} comDesafio={comDesafio} desabilitado={ocupado} />`
- no texto do cabeçalho, troque "limite semanal e bloqueio antes de liberar." por "limite semanal, bloqueio e, pela câmera, prova de vida antes de liberar."

`painel/Painel.tsx`:
- tipo `Acesso` ganha `vivacidade: boolean | null`
- no `<thead>`, depois de `Score`: `<th className="p-2">Prova de vida</th>`
- na linha, depois da célula de score: `<td className="p-2">{a.vivacidade ? '✓' : '—'}</td>`

- [ ] **Step 4: Run tests and build**

Run: `cd facegym-web && npm test -- --run && npm run build`
Expected: PASS e build sem erros de tipo

- [ ] **Step 5: Verificar no navegador**

Suba a pilha local (`docker compose up -d --build`, `ADMIN_PASSWORD=admin12345 scripts/seed-demo.sh`, `cd facegym-web && npm run dev`). No totem:
1. Clique num aluno fictício → liberado com "Sem prova de vida (demo)".
2. "Teste com você" → cadastre o rosto → "Fazer check-in" → siga a seta → liberado com "✓ Prova de vida".
3. Repita (depois de 5 min, por causa do antipassback) sem virar o rosto → "Não deu para confirmar", botão "Tentar de novo".
4. No painel, a coluna "Prova de vida" mostra ✓ e —.
Se o passo 2 reprovar mesmo virando o rosto, não mexa nos limiares aqui: siga para a Task 9 e registre o que aconteceu.

- [ ] **Step 6: Commit**

```bash
git add facegym-web/src
git commit -m "feat(web): totem pede o desafio de vivacidade e mostra o selo de prova de vida"
```

---

### Task 9: Calibração e documentação

**Files:**
- Create: `facegym-biometria/scripts/calibrar_vivacidade.py`
- Modify: `facegym-biometria/CALIBRATION.md`, `README.md`, e `facegym-api/src/main/resources/application.yml` se os limiares mudarem

**Interfaces:**
- Consumes: `InsightFaceEmbedder.analyze`, `giro` (Task 1).

- [ ] **Step 1: Script de calibração**

`facegym-biometria/scripts/calibrar_vivacidade.py`:
```python
"""Mede giro e similaridade de selfies para calibrar a vivacidade.

Uso: python scripts/calibrar_vivacidade.py <pasta>
A pasta tem arquivos frente*.jpg, esquerda*.jpg e direita*.jpg (esquerda/direita da pessoa).
As fotos ficam fora do repositório; só os números vão para o CALIBRATION.md.
"""
import sys
from pathlib import Path
from app.embedder import InsightFaceEmbedder
from app.images import decode_image
from app.liveness import giro

def main(pasta: Path) -> None:
    embedder = InsightFaceEmbedder()
    fotos = sorted(pasta.glob("*.jpg"))
    rostos = {p.name: embedder.analyze(decode_image(p.read_bytes())) for p in fotos}
    referencia = next((r for n, r in rostos.items() if n.startswith("frente") and r is not None), None)
    print(f"{'foto':<24}{'giro':>8}{'similaridade':>14}")
    for nome, rosto in rostos.items():
        if rosto is None:
            print(f"{nome:<24}{'sem rosto':>8}")
            continue
        sim = float(rosto.embedding @ referencia.embedding) if referencia is not None else float("nan")
        print(f"{nome:<24}{giro(rosto.kps):>+8.3f}{sim:>14.3f}")

if __name__ == "__main__":
    main(Path(sys.argv[1]))
```

- [ ] **Step 2: Coletar as selfies (passo humano)**

Peça ao autor para tirar, pela webcam do notebook (a mesma do totem), numa pasta **fora do repositório** (ex.: `~/facegym-calibracao/`): 3 fotos `frente1..3.jpg`, 3 `esquerda1..3.jpg` e 3 `direita1..3.jpg`, virando o rosto como faria na catraca. A foto não pode estar espelhada (a câmera do macOS pelo Photo Booth espelha; use o próprio totem com o console, ou vire a imagem com `sips -f horizontal`).

- [ ] **Step 3: Medir**

Run: `cd facegym-biometria && python scripts/calibrar_vivacidade.py ~/facegym-calibracao`
Expected: frente com `|giro|` perto de 0; esquerda positivo; direita negativo; similaridades acima de 0,5.
Se esquerda sair **negativo**, o sinal está invertido: troque `(nariz[0] - meio_x)` por `(meio_x - nariz[0])` em `app/liveness.py`, inverta os sinais esperados em `tests/test_liveness.py` e rode `pytest` de novo.

- [ ] **Step 4: Escolher os limiares e documentar**

Acrescente ao `facegym-biometria/CALIBRATION.md` uma seção `## Vivacidade` com a tabela impressa (sem as fotos), o giro mínimo das viradas, o máximo das frentes e a similaridade mínima. Regra: `frente` no meio entre o maior `|giro|` de frente e o menor de virada; `virada` perto de 60% do menor giro das viradas (quem vira pouco ainda passa); `mesmaPessoa` abaixo da menor similaridade medida e acima de 0,18 (limiar de dúvida do reconhecimento). Se os valores mudarem, atualize os padrões em `application.yml` (`VIVACIDADE_FRENTE`, `VIVACIDADE_VIRADA`, `VIVACIDADE_MESMA_PESSOA`) e os testes limite de `PoliticaDeVivacidadeTest` só se forem os mesmos números.

- [ ] **Step 5: README**

Em `README.md`:
- na lista de Arquitetura, depois do item de Resiliência:
```markdown
- **Prova de vida** por desafio: o totem sorteia um lado, a pessoa vira o rosto e a biometria mede o giro
  (pelos 5 pontos do detector) e a similaridade entre as duas fotos; a `PoliticaDeVivacidade` decide.
  Foto parada não vira e vídeo gravado não sabe o lado. `VIVACIDADE=obrigatoria` recusa foto única; a
  demo usa `opcional` para os alunos fictícios, o que deixa a vivacidade burlável por quem envia foto única.
```
- em Próximos passos: `Pagamentos, multi-tenant, app do aluno.`

- [ ] **Step 6: Rodar tudo**

Run: `cd facegym-biometria && pytest && cd ../facegym-api && ./mvnw -q verify && cd ../facegym-web && npm test -- --run`
Expected: PASS nas três suítes

- [ ] **Step 7: Commit**

```bash
git add facegym-biometria/scripts/calibrar_vivacidade.py facegym-biometria/CALIBRATION.md README.md facegym-api/src/main/resources/application.yml facegym-biometria/app/liveness.py facegym-biometria/tests/test_liveness.py
git commit -m "docs: calibração da vivacidade e README"
```
