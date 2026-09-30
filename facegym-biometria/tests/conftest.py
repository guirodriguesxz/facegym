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
    """Decide o vetor e o giro pela cor do pixel (0,0) da imagem BGR."""
    def __init__(self, by_rgb: dict[tuple[int, int, int], np.ndarray | None],
                 yaw_by_rgb: dict[tuple[int, int, int], float] | None = None):
        self.by_rgb = by_rgb
        self.yaw_by_rgb = yaw_by_rgb or {}

    def _rgb(self, image_bgr):
        b, g, r = (int(x) for x in image_bgr[0, 0])
        return r, g, b

    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        return self.by_rgb.get(self._rgb(image_bgr))

    def analyze(self, image_bgr: np.ndarray):
        rgb = self._rgb(image_bgr)
        emb = self.by_rgb.get(rgb)
        h, w = image_bgr.shape[:2]
        return None if emb is None else (emb, self.yaw_by_rgb.get(rgb, 0.0), np.array([0, 0, w, h], dtype=np.float32))

class FakeAntiSpoof:
    """Toda foto é "real", exceto as da cor em `fake`."""
    def __init__(self, fake: set[tuple[int, int, int]] | None = None):
        self.fake = fake or set()

    def real_score(self, image_bgr: np.ndarray, bbox) -> float:
        b, g, r = (int(x) for x in image_bgr[0, 0])
        return 0.02 if (r, g, b) in self.fake else 0.98

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
# Mesmo vetor do RED, mas "virado": simula a segunda foto do desafio.
RED_TURNED = (254, 0, 0)
# Mesmo rosto do RED, de frente, mas o anti-spoofing diz que é reprodução (papel/tela).
RED_PRINTED = (253, 0, 0)

@pytest.fixture
def repo():
    return InMemoryFaceRepository()

@pytest.fixture
def client(repo):
    embedder = FakeEmbedder({RED: vec(0), RED_TURNED: vec(0), RED_PRINTED: vec(0), GREEN: vec(1), BLUE: None},
                            {RED_TURNED: 0.45})
    return TestClient(create_app(embedder, repo, KEY, FakeAntiSpoof({RED_PRINTED})))

@pytest.fixture
def auth():
    return {"X-Internal-Key": KEY}
