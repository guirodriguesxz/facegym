import io
from uuid import UUID
import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from app.api import create_app
from app.embedder import Rosto

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
ORANGE = (255, 128, 0)  # mesma pessoa de RED, virada para a esquerda dela

@pytest.fixture
def repo():
    return InMemoryFaceRepository()

@pytest.fixture
def client(repo):
    embedder = FakeEmbedder({RED: (vec(0), 0.0), ORANGE: (vec(0), 0.4), GREEN: (vec(1), 0.0), BLUE: None})
    return TestClient(create_app(embedder, repo, KEY))

@pytest.fixture
def auth():
    return {"X-Internal-Key": KEY}
