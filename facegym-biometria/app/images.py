import io
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_SIDE = 1600

class InvalidImage(Exception):
    pass

def decode_image(data: bytes) -> np.ndarray:
    try:
        img = Image.open(io.BytesIO(data))
        img = ImageOps.exif_transpose(img)
        img = img.convert("RGB")
    except (UnidentifiedImageError, OSError) as e:
        raise InvalidImage() from e
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    return np.ascontiguousarray(np.asarray(img)[:, :, ::-1])
