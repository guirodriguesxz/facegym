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
