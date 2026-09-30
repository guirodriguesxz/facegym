"""Mede giro e similaridade de selfies para calibrar a vivacidade.

Uso (em facegym-biometria/): PYTHONPATH=. .venv/bin/python scripts/calibrar_vivacidade.py <pasta>
A pasta tem arquivos frente*.jpg, esquerda*.jpg e direita*.jpg (esquerda/direita da pessoa).
As fotos ficam fora do repositório; só os números vão para o CALIBRATION.md.
"""
import sys
from pathlib import Path
from app.embedder import InsightFaceEmbedder
from app.images import decode_image
from app.liveness import giro

def main(pasta: Path) -> None:
    fotos = sorted(pasta.expanduser().glob("*.jpg"))
    if not fotos:
        sys.exit(f"Nenhuma foto .jpg em {pasta}")
    embedder = InsightFaceEmbedder()
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
