# scripts/variacoes.py
"""Gera a foto de check-in de cada aluno da demo: recorte, espelho e brilho.
Assim o score fica realista (~0.9) em vez de 1.0 da mesma imagem."""
import sys
from pathlib import Path
from PIL import Image, ImageEnhance, ImageOps

origem, destino = Path(sys.argv[1]), Path(sys.argv[2])
destino.mkdir(parents=True, exist_ok=True)
for foto in sorted(origem.glob("*.jpg")):
    img = Image.open(foto).convert("RGB")
    w, h = img.size
    img = img.crop((int(w * 0.05), int(h * 0.03), int(w * 0.97), int(h * 0.98)))
    img = ImageEnhance.Brightness(ImageOps.mirror(img)).enhance(1.15)
    img.save(destino / foto.name, quality=90)
    print("variação:", destino / foto.name)
