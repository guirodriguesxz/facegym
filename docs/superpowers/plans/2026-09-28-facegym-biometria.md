# FaceGym Biometria — Implementation Plan (1 de 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serviço `facegym-biometria` (FastAPI) que cadastra, identifica e apaga embeddings faciais em Postgres + pgvector, sem nunca guardar foto.

**Architecture:** App criado por fábrica `create_app(embedder, repo, internal_key)`, para os testes injetarem um embedder falso e um repositório em memória. O `InsightFaceEmbedder` (buffalo_s, CPU) e o `PgFaceRepository` (pgvector) são as implementações reais, testadas separadamente.

**Tech Stack:** Python 3.12, FastAPI, Uvicorn, InsightFace + onnxruntime (CPU), Pillow, NumPy, psycopg 3 + pgvector, pytest, httpx, testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-28-facegym-design.md` (seções 2.2, 5, 7, 8)

**Planos seguintes:** 2 = `facegym-api` (Spring, hexagonal, resiliência); 3 = `facegym-web` + deploy + dados de demo.

## Global Constraints

- Embeddings de 512 dimensões, normalizados (norma 1); score = similaridade de cosseno.
- Todos os endpoints `/faces/*` exigem header `X-Internal-Key`; `/health` é público.
- Nunca persistir nem logar imagem ou embedding.
- Imagem > 5 MB → 413. Bytes que não são imagem → 422 `imagem inválida`.
- `PUT` sem rosto → 422 `nenhum rosto encontrado`. `identify`/`compare-demo` sem rosto → 200 `{"alunoId": null, "score": null}`.
- Mais de um rosto → usa o de maior área.
- Deve caber no plano free do Render: **512 MB de RAM**.

## Review Focus

1. **Foto de celular girada (EXIF)** — deve ser corrigida antes da detecção; senão o rosto "some". Teste na Task 3.
2. **PNG com transparência / imagem em tons de cinza** — deve virar RGB sem erro. Teste na Task 3.
3. **Foto enorme (ex.: 4000×3000)** — deve ser reduzida antes do modelo, para não estourar memória nem tempo. Teste na Task 3.
4. **Recadastro do mesmo aluno** — `PUT` repetido substitui, nunca duplica (senão o identify pode devolver vetor antigo). Teste na Task 4.
5. **Identify com banco vazio** — deve responder `alunoId: null`, não 500. Teste na Task 4 e na Task 5.

## Estrutura de arquivos

```
facegym-biometria/
  pyproject.toml
  Dockerfile
  app/
    __init__.py
    config.py        # Settings a partir de env
    images.py        # decode_image: bytes -> array BGR (EXIF, RGB, redução)
    embedder.py      # Protocol Embedder + InsightFaceEmbedder
    repository.py    # Protocol FaceRepository + PgFaceRepository
    api.py           # create_app(...) com rotas e auth
    main.py          # monta o app real (uvicorn app.main:app)
  tests/
    conftest.py      # FakeEmbedder, InMemoryFaceRepository, helpers de imagem
    test_images.py
    test_repository.py
    test_api.py
    test_accuracy.py # marcado @pytest.mark.slow (usa modelo real + LFW)
  spike/
    measure.py       # descartável: memória e latência do modelo
.github/workflows/biometria.yml
```

---

### Task 1: Spike — memória e latência do buffalo_s

Descartável: o objetivo é uma resposta, não código de produção. O resultado decide se seguimos com `buffalo_s`.

**Files:**
- Create: `facegym-biometria/spike/measure.py`
- Create: `facegym-biometria/spike/RESULT.md`

- [ ] **Step 1: Escrever o script de medição**

```python
# facegym-biometria/spike/measure.py
import resource, time, urllib.request
import numpy as np
import cv2
from insightface.app import FaceAnalysis

URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/Pierre-Person.jpg/480px-Pierre-Person.jpg"

def rss_mb() -> float:
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024  # Linux: KB

t0 = time.perf_counter()
app = FaceAnalysis(name="buffalo_s", allowed_modules=["detection", "recognition"],
                   providers=["CPUExecutionProvider"])
app.prepare(ctx_id=-1, det_size=(640, 640))
print(f"load: {time.perf_counter() - t0:.1f}s, rss: {rss_mb():.0f} MB")

img = cv2.imdecode(np.frombuffer(urllib.request.urlopen(URL).read(), np.uint8), cv2.IMREAD_COLOR)
times = []
for _ in range(10):
    t = time.perf_counter()
    faces = app.get(img)
    times.append(time.perf_counter() - t)
print(f"faces: {len(faces)}, p50: {sorted(times)[5]*1000:.0f} ms, max rss: {rss_mb():.0f} MB")
```

- [ ] **Step 2: Rodar dentro de um container limitado a 512 MB**

```bash
cd facegym-biometria
docker run --rm -m 512m -v "$PWD/spike:/spike" python:3.12-slim bash -c \
  "apt-get update -qq && apt-get install -y -qq g++ libgl1 libglib2.0-0 >/dev/null && \
   pip install -q insightface==0.7.3 onnxruntime==1.19.2 opencv-python-headless numpy && \
   python /spike/measure.py"
```

Esperado: imprime `load`, `faces: 1`, `p50` e `max rss`. Se o container morrer com exit 137 (OOM), o modelo **não** cabe.

- [ ] **Step 3: Registrar o resultado e decidir**

Escrever em `spike/RESULT.md` os números medidos e a decisão:
- `max rss` ≤ 400 MB e p50 ≤ 800 ms → seguir com `buffalo_s`.
- Caso contrário → parar e levar os números ao usuário antes da Task 2 (alternativa: `det_size=(320, 320)`, que reduz memória e tempo da detecção).

- [ ] **Step 4: Commit**

```bash
git add facegym-biometria/spike
git commit -m "spike: memória e latência do buffalo_s em 512 MB"
```

---

### Task 2: Esqueleto do serviço, config e autenticação interna

**Files:**
- Create: `facegym-biometria/pyproject.toml`
- Create: `facegym-biometria/app/__init__.py` (vazio)
- Create: `facegym-biometria/app/config.py`
- Create: `facegym-biometria/app/embedder.py` (só o Protocol nesta task)
- Create: `facegym-biometria/app/repository.py` (só o Protocol nesta task)
- Create: `facegym-biometria/app/api.py`
- Create: `facegym-biometria/tests/conftest.py`
- Test: `facegym-biometria/tests/test_api.py`

**Interfaces:**
- Produces:
  - `class Embedder(Protocol): def embed(self, image_bgr: np.ndarray) -> np.ndarray | None` — vetor float32 (512,) normalizado, ou `None` sem rosto.
  - `class FaceRepository(Protocol)`: `upsert(aluno_id: UUID, embedding: np.ndarray) -> None`; `nearest(embedding: np.ndarray) -> tuple[UUID, float] | None`; `delete(aluno_id: UUID) -> None`.
  - `create_app(embedder: Embedder, repo: FaceRepository, internal_key: str) -> FastAPI`.

- [ ] **Step 1: pyproject**

```toml
# facegym-biometria/pyproject.toml
[project]
name = "facegym-biometria"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
  "fastapi==0.115.0",
  "uvicorn[standard]==0.30.6",
  "python-multipart==0.0.10",
  "numpy==1.26.4",
  "pillow==10.4.0",
  "insightface==0.7.3",
  "onnxruntime==1.19.2",
  "opencv-python-headless==4.10.0.84",
  "psycopg[binary]==3.2.3",
  "pgvector==0.3.5",
]

[project.optional-dependencies]
dev = ["pytest==8.3.3", "httpx==0.27.2", "testcontainers[postgres]==4.8.1", "scikit-learn==1.5.2"]

[tool.pytest.ini_options]
markers = ["slow: usa o modelo real e baixa o dataset LFW"]
addopts = "-m 'not slow'"
```

- [ ] **Step 2: Protocols e config**

```python
# facegym-biometria/app/embedder.py
from typing import Protocol
import numpy as np

class Embedder(Protocol):
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None: ...
```

```python
# facegym-biometria/app/repository.py
from typing import Protocol
from uuid import UUID
import numpy as np

class FaceRepository(Protocol):
    def upsert(self, aluno_id: UUID, embedding: np.ndarray) -> None: ...
    def nearest(self, embedding: np.ndarray) -> tuple[UUID, float] | None: ...
    def delete(self, aluno_id: UUID) -> None: ...
```

```python
# facegym-biometria/app/config.py
import os
from dataclasses import dataclass

@dataclass(frozen=True)
class Settings:
    database_url: str
    internal_key: str

    @staticmethod
    def from_env() -> "Settings":
        key = os.environ.get("INTERNAL_KEY", "")
        if len(key) < 16:
            raise RuntimeError("INTERNAL_KEY precisa ter pelo menos 16 caracteres")
        return Settings(
            database_url=os.environ.get("DATABASE_URL", "postgresql://biometria:biometria@localhost:5433/biometria"),
            internal_key=key,
        )
```

- [ ] **Step 3: Fakes de teste**

```python
# facegym-biometria/tests/conftest.py
import io
from uuid import UUID
import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from app.api import create_app

KEY = "test-key-0123456789"

def vec(i: int) -> np.ndarray:
    """Vetor unitário distinto por índice (512 dims)."""
    v = np.zeros(512, dtype=np.float32)
    v[i] = 1.0
    return v

def png(color: tuple[int, int, int], size=(64, 64), mode="RGB") -> bytes:
    buf = io.BytesIO()
    Image.new(mode, size, color if mode == "RGB" else color[0]).save(buf, format="PNG")
    return buf.getvalue()

class FakeEmbedder:
    """Decide o vetor pela cor do pixel (0,0) da imagem BGR."""
    def __init__(self, by_rgb: dict[tuple[int, int, int], np.ndarray | None]):
        self.by_rgb = by_rgb

    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        b, g, r = (int(x) for x in image_bgr[0, 0])
        return self.by_rgb.get((r, g, b))

class InMemoryFaceRepository:
    def __init__(self):
        self.rows: dict[UUID, np.ndarray] = {}

    def upsert(self, aluno_id, embedding):
        self.rows[aluno_id] = embedding

    def nearest(self, embedding):
        if not self.rows:
            return None
        aluno_id, best = max(self.rows.items(), key=lambda kv: float(kv[1] @ embedding))
        return aluno_id, float(best @ embedding)

    def delete(self, aluno_id):
        self.rows.pop(aluno_id, None)

RED, GREEN, BLUE = (255, 0, 0), (0, 255, 0), (0, 0, 255)

@pytest.fixture
def repo():
    return InMemoryFaceRepository()

@pytest.fixture
def client(repo):
    embedder = FakeEmbedder({RED: vec(0), GREEN: vec(1), BLUE: None})
    return TestClient(create_app(embedder, repo, KEY))

@pytest.fixture
def auth():
    return {"X-Internal-Key": KEY}
```

- [ ] **Step 4: Testes de auth e health (falhando)**

```python
# facegym-biometria/tests/test_api.py
from uuid import uuid4
from tests.conftest import RED, png

def test_health_is_public(client):
    assert client.get("/health").json() == {"status": "UP"}

def test_faces_require_internal_key(client):
    r = client.post("/faces/identify", files={"image": ("a.png", png(RED), "image/png")})
    assert r.status_code == 401

def test_wrong_internal_key_is_rejected(client):
    r = client.delete(f"/faces/{uuid4()}", headers={"X-Internal-Key": "errada"})
    assert r.status_code == 401
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `cd facegym-biometria && pip install -e ".[dev]" && pytest tests/test_api.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.api'`.

- [ ] **Step 6: Implementar `create_app` com health e auth**

```python
# facegym-biometria/app/api.py
import hmac
from fastapi import Depends, FastAPI, Header, HTTPException
from app.embedder import Embedder
from app.repository import FaceRepository

def create_app(embedder: Embedder, repo: FaceRepository, internal_key: str) -> FastAPI:
    app = FastAPI(title="FaceGym Biometria")

    def require_key(x_internal_key: str = Header(default="")) -> None:
        if not hmac.compare_digest(x_internal_key.encode(), internal_key.encode()):
            raise HTTPException(status_code=401, detail="não autorizado")

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.delete("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def delete_face(aluno_id: str):
        raise HTTPException(status_code=501)

    @app.post("/faces/identify", dependencies=[Depends(require_key)])
    def identify():
        raise HTTPException(status_code=501)

    return app
```

- [ ] **Step 7: Rodar e ver passar**

Run: `pytest tests/test_api.py -v`
Expected: 3 passed.

- [ ] **Step 8: Commit**

```bash
git add facegym-biometria
git commit -m "feat(biometria): esqueleto FastAPI com auth interna"
```

---

### Task 3: Decodificação de imagem e embedder InsightFace

**Files:**
- Create: `facegym-biometria/app/images.py`
- Modify: `facegym-biometria/app/embedder.py`
- Test: `facegym-biometria/tests/test_images.py`

**Interfaces:**
- Produces:
  - `class InvalidImage(Exception)`
  - `MAX_SIDE = 1600`
  - `decode_image(data: bytes) -> np.ndarray` — BGR uint8 (h, w, 3), EXIF aplicado, lado maior ≤ `MAX_SIDE`. Lança `InvalidImage`.
  - `class InsightFaceEmbedder` (implementa `Embedder`).

- [ ] **Step 1: Testes (falhando)**

```python
# facegym-biometria/tests/test_images.py
import io
import numpy as np
import pytest
from PIL import Image
from app.images import MAX_SIDE, InvalidImage, decode_image

def encode(img: Image.Image, fmt="JPEG", **kw) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format=fmt, **kw)
    return buf.getvalue()

def test_returns_bgr_uint8():
    arr = decode_image(encode(Image.new("RGB", (10, 10), (255, 0, 0)), "PNG"))
    assert arr.dtype == np.uint8 and arr.shape == (10, 10, 3)
    assert tuple(arr[0, 0]) == (0, 0, 255)  # vermelho em BGR

def test_rejects_non_image_bytes():
    with pytest.raises(InvalidImage):
        decode_image(b"isto nao e uma imagem")

def test_converts_rgba_and_grayscale_to_three_channels():
    assert decode_image(encode(Image.new("RGBA", (8, 8), (0, 0, 0, 0)), "PNG")).shape == (8, 8, 3)
    assert decode_image(encode(Image.new("L", (8, 8), 128), "PNG")).shape == (8, 8, 3)

def test_downscales_large_images_keeping_aspect():
    arr = decode_image(encode(Image.new("RGB", (4000, 3000))))
    assert max(arr.shape[:2]) == MAX_SIDE
    assert arr.shape[:2] == (1200, 1600)

def test_applies_exif_rotation():
    img = Image.new("RGB", (40, 20))  # largura > altura
    exif = img.getexif()
    exif[0x0112] = 6  # Orientation: girar 90° (foto de celular em pé)
    arr = decode_image(encode(img, exif=exif))
    assert arr.shape[:2] == (40, 20)  # agora altura > largura
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `pytest tests/test_images.py -v`
Expected: FAIL — `No module named 'app.images'`.

- [ ] **Step 3: Implementar**

```python
# facegym-biometria/app/images.py
import io
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_SIDE = 1600

class InvalidImage(Exception):
    pass

def decode_image(data: bytes) -> np.ndarray:
    try:
        img = Image.open(io.BytesIO(data))
        img = ImageOps.exif_transpose(img)
        img = img.convert("RGB")
    except (UnidentifiedImageError, OSError) as e:
        raise InvalidImage() from e
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    return np.ascontiguousarray(np.asarray(img)[:, :, ::-1])
```

- [ ] **Step 4: Rodar e ver passar**

Run: `pytest tests/test_images.py -v`
Expected: 5 passed.

- [ ] **Step 5: Implementar o embedder real**

Sem teste unitário aqui (depende do modelo de ~100 MB); é coberto pelo teste de acurácia da Task 6.

```python
# facegym-biometria/app/embedder.py
from typing import Protocol
import numpy as np

class Embedder(Protocol):
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None: ...

class InsightFaceEmbedder:
    def __init__(self, model: str = "buffalo_s", det_size: tuple[int, int] = (640, 640)):
        from insightface.app import FaceAnalysis  # import tardio: testes rápidos não carregam o modelo
        self._app = FaceAnalysis(name=model, allowed_modules=["detection", "recognition"],
                                 providers=["CPUExecutionProvider"])
        self._app.prepare(ctx_id=-1, det_size=det_size)

    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        faces = self._app.get(image_bgr)
        if not faces:
            return None
        biggest = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
        return biggest.normed_embedding.astype(np.float32)
```

Se o spike (Task 1) decidiu `det_size=(320, 320)`, usar esse valor como padrão aqui.

- [ ] **Step 6: Commit**

```bash
git add facegym-biometria/app/images.py facegym-biometria/app/embedder.py facegym-biometria/tests/test_images.py
git commit -m "feat(biometria): decodificação de imagem e embedder InsightFace"
```

---

### Task 4: Repositório pgvector

**Files:**
- Modify: `facegym-biometria/app/repository.py`
- Test: `facegym-biometria/tests/test_repository.py`

**Interfaces:**
- Consumes: `FaceRepository` (Task 2).
- Produces: `class PgFaceRepository(database_url: str)` com `init_schema() -> None` + métodos do Protocol.

- [ ] **Step 1: Testes (falhando)**

```python
# facegym-biometria/tests/test_repository.py
from uuid import uuid4
import numpy as np
import pytest
from testcontainers.postgres import PostgresContainer
from app.repository import PgFaceRepository
from tests.conftest import vec

@pytest.fixture(scope="module")
def database_url():
    with PostgresContainer("pgvector/pgvector:pg16", driver=None) as pg:
        yield pg.get_connection_url()

@pytest.fixture
def repo(database_url):
    r = PgFaceRepository(database_url)
    r.init_schema()
    r.clear()
    return r

def test_nearest_on_empty_table_returns_none(repo):
    assert repo.nearest(vec(0)) is None

def test_nearest_returns_closest_with_cosine_similarity(repo):
    a, b = uuid4(), uuid4()
    repo.upsert(a, vec(0))
    repo.upsert(b, vec(1))
    probe = (vec(0) * 0.9 + vec(1) * 0.1)
    probe /= np.linalg.norm(probe)
    aluno_id, score = repo.nearest(probe)
    assert aluno_id == a
    assert score == pytest.approx(float(probe @ vec(0)), abs=1e-4)

def test_upsert_replaces_instead_of_duplicating(repo):
    a = uuid4()
    repo.upsert(a, vec(0))
    repo.upsert(a, vec(1))
    assert repo.count() == 1
    assert repo.nearest(vec(1)) == (a, pytest.approx(1.0, abs=1e-4))

def test_delete_is_idempotent(repo):
    a = uuid4()
    repo.upsert(a, vec(0))
    repo.delete(a)
    repo.delete(a)
    assert repo.nearest(vec(0)) is None
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `pytest tests/test_repository.py -v` (precisa do Docker aberto)
Expected: FAIL — `cannot import name 'PgFaceRepository'`.

- [ ] **Step 3: Implementar**

```python
# facegym-biometria/app/repository.py
from typing import Protocol
from uuid import UUID
import numpy as np
import psycopg
from pgvector.psycopg import register_vector

class FaceRepository(Protocol):
    def upsert(self, aluno_id: UUID, embedding: np.ndarray) -> None: ...
    def nearest(self, embedding: np.ndarray) -> tuple[UUID, float] | None: ...
    def delete(self, aluno_id: UUID) -> None: ...

class PgFaceRepository:
    def __init__(self, database_url: str):
        self._url = database_url

    def _connect(self) -> psycopg.Connection:
        conn = psycopg.connect(self._url, autocommit=True)
        register_vector(conn)
        return conn

    def init_schema(self) -> None:
        with psycopg.connect(self._url, autocommit=True) as conn:
            conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        with self._connect() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS face_embedding (
                    aluno_id  uuid PRIMARY KEY,
                    embedding vector(512) NOT NULL,
                    criado_em timestamptz NOT NULL DEFAULT now()
                )""")

    def upsert(self, aluno_id: UUID, embedding: np.ndarray) -> None:
        with self._connect() as conn:
            conn.execute(
                """INSERT INTO face_embedding (aluno_id, embedding) VALUES (%s, %s)
                   ON CONFLICT (aluno_id) DO UPDATE SET embedding = EXCLUDED.embedding, criado_em = now()""",
                (aluno_id, embedding))

    def nearest(self, embedding: np.ndarray) -> tuple[UUID, float] | None:
        with self._connect() as conn:
            row = conn.execute(
                """SELECT aluno_id, 1 - (embedding <=> %s) AS score
                   FROM face_embedding ORDER BY embedding <=> %s LIMIT 1""",
                (embedding, embedding)).fetchone()
        return (row[0], float(row[1])) if row else None

    def delete(self, aluno_id: UUID) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM face_embedding WHERE aluno_id = %s", (aluno_id,))

    # usados só por testes
    def clear(self) -> None:
        with self._connect() as conn:
            conn.execute("TRUNCATE face_embedding")

    def count(self) -> int:
        with self._connect() as conn:
            return conn.execute("SELECT count(*) FROM face_embedding").fetchone()[0]
```

- [ ] **Step 4: Rodar e ver passar**

Run: `pytest tests/test_repository.py -v`
Expected: 4 passed.

- [ ] **Step 5: Commit**

```bash
git add facegym-biometria/app/repository.py facegym-biometria/tests/test_repository.py
git commit -m "feat(biometria): repositório pgvector com upsert e busca por cosseno"
```

---

### Task 5: Endpoints de cadastro, identificação e exclusão

**Files:**
- Modify: `facegym-biometria/app/api.py`
- Create: `facegym-biometria/app/main.py`
- Test: `facegym-biometria/tests/test_api.py`

**Interfaces:**
- Consumes: `decode_image`, `InvalidImage` (Task 3); `Embedder`, `FaceRepository` (Task 2); `PgFaceRepository` e `InsightFaceEmbedder` (Tasks 3–4); `Settings` (Task 2).
- Produces (contrato HTTP usado pelo plano 2):
  - `PUT /faces/{alunoId}` multipart `image` → 204 | 413 | 422 `{"detail": "imagem inválida" | "nenhum rosto encontrado"}`.
  - `POST /faces/identify` multipart `image` → 200 `{"alunoId": "<uuid>" | null, "score": <float> | null}` | 413 | 422.
  - `POST /faces/compare-demo` → mesmo contrato do identify, nunca escreve.
  - `DELETE /faces/{alunoId}` → 204 (idempotente).
  - `alunoId` inválido (não UUID) → 422.

- [ ] **Step 1: Testes (falhando) — adicionar ao `tests/test_api.py`**

```python
from uuid import uuid4
from tests.conftest import BLUE, GREEN, RED, png

def upload(color):
    return {"image": ("f.png", png(color), "image/png")}

def test_register_then_identify(client, auth):
    aluno = uuid4()
    assert client.put(f"/faces/{aluno}", files=upload(RED), headers=auth).status_code == 204
    r = client.post("/faces/identify", files=upload(RED), headers=auth)
    assert r.status_code == 200
    assert r.json() == {"alunoId": str(aluno), "score": 1.0}

def test_identify_with_empty_database_returns_null(client, auth):
    r = client.post("/faces/identify", files=upload(RED), headers=auth)
    assert r.json() == {"alunoId": None, "score": None}

def test_identify_without_face_returns_null(client, auth):
    client.put(f"/faces/{uuid4()}", files=upload(RED), headers=auth)
    r = client.post("/faces/identify", files=upload(BLUE), headers=auth)
    assert r.status_code == 200 and r.json() == {"alunoId": None, "score": None}

def test_register_without_face_is_422(client, auth, repo):
    r = client.put(f"/faces/{uuid4()}", files=upload(BLUE), headers=auth)
    assert r.status_code == 422 and r.json()["detail"] == "nenhum rosto encontrado"
    assert repo.rows == {}

def test_non_image_is_422(client, auth):
    r = client.post("/faces/identify", files={"image": ("x.txt", b"oi", "text/plain")}, headers=auth)
    assert r.status_code == 422 and r.json()["detail"] == "imagem inválida"

def test_image_over_5mb_is_413(client, auth):
    big = b"\x00" * (5 * 1024 * 1024 + 1)
    r = client.post("/faces/identify", files={"image": ("big.png", big, "image/png")}, headers=auth)
    assert r.status_code == 413

def test_invalid_aluno_id_is_422(client, auth):
    assert client.put("/faces/nao-e-uuid", files=upload(RED), headers=auth).status_code == 422

def test_delete_removes_and_is_idempotent(client, auth):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files=upload(RED), headers=auth)
    assert client.delete(f"/faces/{aluno}", headers=auth).status_code == 204
    assert client.delete(f"/faces/{aluno}", headers=auth).status_code == 204
    assert client.post("/faces/identify", files=upload(RED), headers=auth).json()["alunoId"] is None

def test_compare_demo_never_writes(client, auth, repo):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files=upload(GREEN), headers=auth)
    before = dict(repo.rows)
    r = client.post("/faces/compare-demo", files=upload(GREEN), headers=auth)
    assert r.json()["alunoId"] == str(aluno)
    assert repo.rows == before
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `pytest tests/test_api.py -v`
Expected: os 3 testes de auth passam; os novos falham (501/404).

- [ ] **Step 3: Implementar as rotas**

```python
# facegym-biometria/app/api.py
import hmac
from uuid import UUID
from fastapi import Depends, FastAPI, File, Header, HTTPException, Response, UploadFile
from app.embedder import Embedder
from app.images import InvalidImage, decode_image
from app.repository import FaceRepository

MAX_BYTES = 5 * 1024 * 1024

def create_app(embedder: Embedder, repo: FaceRepository, internal_key: str) -> FastAPI:
    app = FastAPI(title="FaceGym Biometria")

    def require_key(x_internal_key: str = Header(default="")) -> None:
        if not hmac.compare_digest(x_internal_key.encode(), internal_key.encode()):
            raise HTTPException(status_code=401, detail="não autorizado")

    async def read_embedding(image: UploadFile):
        data = await image.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise HTTPException(status_code=413, detail="imagem maior que 5 MB")
        try:
            return embedder.embed(decode_image(data))
        except InvalidImage:
            raise HTTPException(status_code=422, detail="imagem inválida")

    async def identify_impl(image: UploadFile) -> dict:
        embedding = await read_embedding(image)
        match = repo.nearest(embedding) if embedding is not None else None
        if match is None:
            return {"alunoId": None, "score": None}
        aluno_id, score = match
        return {"alunoId": str(aluno_id), "score": round(score, 4)}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.put("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    async def register(aluno_id: UUID, image: UploadFile = File(...)):
        embedding = await read_embedding(image)
        if embedding is None:
            raise HTTPException(status_code=422, detail="nenhum rosto encontrado")
        repo.upsert(aluno_id, embedding)
        return Response(status_code=204)

    @app.post("/faces/identify", dependencies=[Depends(require_key)])
    async def identify(image: UploadFile = File(...)):
        return await identify_impl(image)

    @app.post("/faces/compare-demo", dependencies=[Depends(require_key)])
    async def compare_demo(image: UploadFile = File(...)):
        # Mesmo cálculo do identify; rota separada para o plano 2 poder
        # aplicar limites próprios à câmera de visitantes.
        return await identify_impl(image)

    @app.delete("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def delete_face(aluno_id: UUID):
        repo.delete(aluno_id)
        return Response(status_code=204)

    return app
```

- [ ] **Step 4: Rodar e ver passar**

Run: `pytest tests/test_api.py -v`
Expected: 12 passed.

- [ ] **Step 5: Montar o app real**

```python
# facegym-biometria/app/main.py
import logging
from app.api import create_app
from app.config import Settings
from app.embedder import InsightFaceEmbedder
from app.repository import PgFaceRepository

# Só níveis e mensagens próprias; nunca logar corpo de requisição (imagem).
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

settings = Settings.from_env()
repo = PgFaceRepository(settings.database_url)
repo.init_schema()
app = create_app(InsightFaceEmbedder(), repo, settings.internal_key)
```

- [ ] **Step 6: Commit**

```bash
git add facegym-biometria/app facegym-biometria/tests/test_api.py
git commit -m "feat(biometria): endpoints de cadastro, identificação e exclusão"
```

---

### Task 6: Teste de acurácia com modelo real e calibração dos limiares

A spec usa 0.80/0.60 como limiares iniciais, mas o cosseno do InsightFace para a mesma pessoa costuma ficar abaixo disso. Esta task mede os valores reais e registra os limiares recomendados para o plano 2.

**Files:**
- Test: `facegym-biometria/tests/test_accuracy.py`
- Create: `facegym-biometria/CALIBRATION.md`

**Interfaces:**
- Consumes: `InsightFaceEmbedder` (Task 3).
- Produces: `CALIBRATION.md` com `LIMIAR_ACEITE` e `LIMIAR_DUVIDA` recomendados (lidos pelo plano 2).

- [ ] **Step 1: Escrever o teste**

Usa o LFW baixado em tempo de teste pelo scikit-learn (não é redistribuído no repositório).

```python
# facegym-biometria/tests/test_accuracy.py
import itertools
import numpy as np
import pytest

pytestmark = pytest.mark.slow

@pytest.fixture(scope="module")
def embeddings():
    from sklearn.datasets import fetch_lfw_people
    from app.embedder import InsightFaceEmbedder
    lfw = fetch_lfw_people(min_faces_per_person=20, resize=1.0, color=True)
    embedder = InsightFaceEmbedder()
    by_person: dict[int, list[np.ndarray]] = {}
    for img, target in zip(lfw.images, lfw.target):
        if len(by_person.get(target, [])) >= 5:
            continue
        bgr = np.ascontiguousarray((img[:, :, ::-1]).astype(np.uint8))
        e = embedder.embed(bgr)
        if e is not None:
            by_person.setdefault(target, []).append(e)
    return {p: v for p, v in by_person.items() if len(v) >= 2}

def scores(embeddings):
    same = [float(a @ b) for v in embeddings.values() for a, b in itertools.combinations(v, 2)]
    people = list(embeddings.values())
    diff = [float(a[0] @ b[0]) for a, b in itertools.combinations(people, 2)]
    return np.array(same), np.array(diff)

def test_same_person_scores_higher_than_different_people(embeddings):
    same, diff = scores(embeddings)
    print(f"\nmesma pessoa: p5={np.percentile(same, 5):.3f} mediana={np.median(same):.3f}")
    print(f"pessoas diferentes: p99={np.percentile(diff, 99):.3f} max={diff.max():.3f}")
    # O modelo precisa separar bem: quase toda comparação da mesma pessoa acima
    # da pior comparação entre pessoas diferentes.
    assert np.percentile(same, 5) > np.percentile(diff, 99)
```

- [ ] **Step 2: Rodar**

Run: `pytest -m slow tests/test_accuracy.py -v -s`
Expected: PASS, imprimindo os percentis. (O primeiro run baixa o LFW, ~200 MB, e o modelo.)

- [ ] **Step 3: Registrar a calibração**

Escrever `CALIBRATION.md` com os números impressos e os limiares:
- `LIMIAR_ACEITE` = o maior entre `p99(diferentes) + 0.05` e `p5(mesma pessoa)`, arredondado a 2 casas.
- `LIMIAR_DUVIDA` = `p99(diferentes)`, arredondado a 2 casas.

Esses valores substituem 0.80/0.60 como padrão no plano 2 (a spec já os declara configuráveis).

- [ ] **Step 4: Commit**

```bash
git add facegym-biometria/tests/test_accuracy.py facegym-biometria/CALIBRATION.md
git commit -m "test(biometria): acurácia no LFW e calibração dos limiares"
```

---

### Task 7: Dockerfile, CI e verificação de logs

**Files:**
- Create: `facegym-biometria/Dockerfile`
- Create: `.github/workflows/biometria.yml`
- Test: `facegym-biometria/tests/test_api.py`

- [ ] **Step 1: Teste de que a imagem não aparece nos logs (falhando se alguém logar o corpo)**

```python
# adicionar em tests/test_api.py
import logging

def test_image_bytes_never_logged(client, auth, caplog):
    marker = png(RED)
    with caplog.at_level(logging.DEBUG):
        client.put(f"/faces/{uuid4()}", files={"image": ("f.png", marker, "image/png")}, headers=auth)
    assert marker[:32].hex() not in caplog.text
    assert "embedding" not in caplog.text.lower()
```

Run: `pytest tests/test_api.py::test_image_bytes_never_logged -v`
Expected: PASS (o app não loga corpo; o teste protege contra regressão).

- [ ] **Step 2: Dockerfile**

O modelo é baixado no build para o container não baixar a cada start (o Render free hiberna e reinicia).

```dockerfile
# facegym-biometria/Dockerfile
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends g++ libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pyproject.toml .
COPY app ./app
RUN pip install --no-cache-dir . \
    && python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_s', allowed_modules=['detection','recognition'], providers=['CPUExecutionProvider']).prepare(ctx_id=-1)"
EXPOSE 8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1"]
```

- [ ] **Step 3: Build e smoke test local**

```bash
cd facegym-biometria
docker build -t facegym-biometria .
docker network create fg-test
docker run -d --name fg-db --network fg-test -e POSTGRES_USER=biometria -e POSTGRES_PASSWORD=biometria -e POSTGRES_DB=biometria pgvector/pgvector:pg16
docker run -d --name fg-bio --network fg-test -p 8000:8000 -m 512m \
  -e INTERNAL_KEY=local-dev-key-0123456789 \
  -e DATABASE_URL=postgresql://biometria:biometria@fg-db:5432/biometria facegym-biometria
curl -s localhost:8000/health
docker stats --no-stream fg-bio
docker rm -f fg-bio fg-db && docker network rm fg-test
```

Expected: `{"status":"UP"}` e uso de memória abaixo de 512 MB.

- [ ] **Step 4: CI**

```yaml
# .github/workflows/biometria.yml
name: biometria
on:
  push:
    paths: ["facegym-biometria/**", ".github/workflows/biometria.yml"]
  pull_request:
    paths: ["facegym-biometria/**"]
jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: facegym-biometria
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
      - run: pip install -e ".[dev]"
      - run: pytest -v
```

(O teste `slow` de acurácia roda só local; baixar LFW + modelo em todo push não compensa.)

- [ ] **Step 5: Commit**

```bash
git add facegym-biometria/Dockerfile .github/workflows/biometria.yml facegym-biometria/tests/test_api.py
git commit -m "chore(biometria): Dockerfile, CI e teste de higiene de logs"
```
