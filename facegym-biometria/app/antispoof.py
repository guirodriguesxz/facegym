"""Anti-spoofing passivo: diz se o rosto na foto é de uma pessoa ou de uma reprodução (papel, tela).

Usa os dois MiniFASNet do Silent-Face-Anti-Spoofing (Minivision, Apache-2.0), convertidos para ONNX
(ver models/NOTICE.md). Cada rede olha um recorte do rosto em escala diferente; a média das duas
probabilidades de "rosto real" é o score.
"""
import hashlib
from pathlib import Path
from typing import Protocol
import numpy as np

MODELS = Path(__file__).resolve().parent.parent / "models"
# (arquivo, escala do recorte em volta da caixa do rosto, SHA-256 do ONNX)
REDES = [
    ("minifasnet_v2_2.7.onnx", 2.7, "40572b4fafa4c8508aa7ea8b29fd6a9cfd7b394165c391a84251d4ad053d161f"),
    ("minifasnet_v1se_4.0.onnx", 4.0, "175d4ce2d8aad460df17aa52f6ecfd03cf79302c5c6b9628e91d067f25e428cf"),
]
LADO = 80
# Abaixo disto a foto é tratada como reprodução. 0.5 = decisão do modelo original (classe mais provável).
REAL_MIN = 0.5


class AntiSpoof(Protocol):
    def real_score(self, image_bgr: np.ndarray, bbox: np.ndarray) -> float:
        """bbox: x1, y1, x2, y2 do rosto. Devolve de 0 (reprodução) a 1 (rosto real)."""
        ...


def recortar(img: np.ndarray, bbox: np.ndarray, escala: float) -> np.ndarray:
    """Recorte quadrado-proporcional em volta do rosto, como o generate_patches.py original."""
    import cv2

    altura, largura = img.shape[:2]
    x, y = float(bbox[0]), float(bbox[1])
    w, h = max(float(bbox[2] - bbox[0]), 1.0), max(float(bbox[3] - bbox[1]), 1.0)
    escala = min((altura - 1) / h, (largura - 1) / w, escala)
    nw, nh = w * escala, h * escala
    cx, cy = x + w / 2, y + h / 2
    esq, topo, dir_, base = cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2
    if esq < 0:
        dir_ -= esq
        esq = 0
    if topo < 0:
        base -= topo
        topo = 0
    if dir_ > largura - 1:
        esq -= dir_ - largura + 1
        dir_ = largura - 1
    if base > altura - 1:
        topo -= base - altura + 1
        base = altura - 1
    return cv2.resize(img[int(topo):int(base) + 1, int(esq):int(dir_) + 1], (LADO, LADO))


class MiniFASNetAntiSpoof:
    def __init__(self, models: Path = MODELS):
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 1
        opts.inter_op_num_threads = 1
        opts.enable_cpu_mem_arena = False
        self._redes = []
        for nome, escala, sha in REDES:
            dados = (models / nome).read_bytes()
            if hashlib.sha256(dados).hexdigest() != sha:
                raise RuntimeError(f"modelo anti-spoofing {nome} não confere com o SHA-256 esperado")
            self._redes.append((ort.InferenceSession(dados, sess_options=opts, providers=["CPUExecutionProvider"]), escala))

    def real_score(self, image_bgr: np.ndarray, bbox: np.ndarray) -> float:
        total = 0.0
        for sessao, escala in self._redes:
            # BGR, 0–255, sem normalização: é o que as redes viram no treino
            x = recortar(image_bgr, bbox, escala).transpose(2, 0, 1)[None].astype(np.float32)
            logits = sessao.run(None, {"input": x})[0][0].astype(np.float64)
            probs = np.exp(logits - logits.max())
            total += float(probs[1] / probs.sum())  # classe 1 = rosto real
        return total / len(self._redes)
