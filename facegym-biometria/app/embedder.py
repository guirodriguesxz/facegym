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
