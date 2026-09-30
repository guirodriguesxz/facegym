from pathlib import Path
from typing import Protocol
import numpy as np

class Embedder(Protocol):
    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None: ...
    def analyze(self, image_bgr: np.ndarray) -> tuple[np.ndarray, float, np.ndarray] | None:
        """Vetor do maior rosto, o giro dele (ver app.liveness.yaw) e a caixa x1, y1, x2, y2."""
        ...

class InsightFaceEmbedder:
    """Carrega só detecção e reconhecimento do pacote, com 1 thread e sem arena de memória.

    O FaceAnalysis abre os 5 modelos do pacote e cria um pool de threads por núcleo: passa de
    600 MB na subida e não cabe nos 512 MB do plano free do Render.
    """

    # 320 basta para o rosto de um totem/selfie e custa metade de 640 na CPU fracionada do plano free
    def __init__(self, model: str = "buffalo_s", det_size: tuple[int, int] = (320, 320)):
        # import tardio: testes rápidos não carregam o modelo
        import onnxruntime as ort
        from insightface.model_zoo.arcface_onnx import ArcFaceONNX
        from insightface.model_zoo.retinaface import RetinaFace
        from insightface.utils.storage import ensure_available

        root = Path(ensure_available("models", model, root="~/.insightface"))
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 1
        opts.inter_op_num_threads = 1
        opts.enable_cpu_mem_arena = False

        def sessao(nome: str):
            return ort.InferenceSession(str(root / nome), sess_options=opts, providers=["CPUExecutionProvider"])

        det = next(root.glob("det_*.onnx")).name
        rec = next(p.name for p in root.glob("*.onnx") if p.name.startswith(("w600k", "glintr")))
        self._det = RetinaFace(model_file=str(root / det), session=sessao(det))
        self._det.prepare(-1, input_size=det_size, det_thresh=0.5)
        self._rec = ArcFaceONNX(model_file=str(root / rec), session=sessao(rec))
        self._rec.prepare(-1)

    def embed(self, image_bgr: np.ndarray) -> np.ndarray | None:
        analise = self.analyze(image_bgr)
        return analise[0] if analise is not None else None

    def analyze(self, image_bgr: np.ndarray) -> tuple[np.ndarray, float, np.ndarray] | None:
        from insightface.app.common import Face
        from app.liveness import yaw

        bboxes, kpss = self._det.detect(image_bgr, max_num=0, metric="default")
        if bboxes.shape[0] == 0:
            return None
        areas = (bboxes[:, 2] - bboxes[:, 0]) * (bboxes[:, 3] - bboxes[:, 1])
        i = int(np.argmax(areas))
        face = Face(bbox=bboxes[i, :4], kps=kpss[i], det_score=bboxes[i, 4])
        self._rec.get(image_bgr, face)
        return face.normed_embedding.astype(np.float32), yaw(kpss[i]), bboxes[i, :4]
