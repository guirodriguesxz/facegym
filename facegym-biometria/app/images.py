import io
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_SIDE = 1600
# Recusa antes de decodificar: uma "bomba" de descompressão cabe em poucos KB
# e ocuparia centenas de MB ao ser aberta.
MAX_PIXELS = 40_000_000

class InvalidImage(Exception):
    pass

def decode_image(data: bytes) -> np.ndarray:
    try:
        img = Image.open(io.BytesIO(data))
        if img.width * img.height > MAX_PIXELS:
            raise InvalidImage()
        img.draft("RGB", (MAX_SIDE, MAX_SIDE))  # JPEG: decodifica já reduzido
        img = ImageOps.exif_transpose(img)
        img = img.convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError, SyntaxError, Image.DecompressionBombError) as e:
        raise InvalidImage() from e
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    return np.ascontiguousarray(np.asarray(img)[:, :, ::-1])
