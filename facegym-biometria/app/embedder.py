from typing import Protocol
import numpy as np

class Embedder(Protocol):
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None: ...
