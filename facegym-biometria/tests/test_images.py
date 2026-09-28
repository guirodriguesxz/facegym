import io
import numpy as np
import pytest
from PIL import Image
from app.images import MAX_SIDE, InvalidImage, decode_image

def encode(img: Image.Image, fmt="JPEG", **kw) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format=fmt, **kw)
    return buf.getvalue()

def test_returns_bgr_uint8():
    arr = decode_image(encode(Image.new("RGB", (10, 10), (255, 0, 0)), "PNG"))
    assert arr.dtype == np.uint8 and arr.shape == (10, 10, 3)
    assert tuple(arr[0, 0]) == (0, 0, 255)  # vermelho em BGR

def test_rejects_non_image_bytes():
    with pytest.raises(InvalidImage):
        decode_image(b"isto nao e uma imagem")

def test_converts_rgba_and_grayscale_to_three_channels():
    assert decode_image(encode(Image.new("RGBA", (8, 8), (0, 0, 0, 0)), "PNG")).shape == (8, 8, 3)
    assert decode_image(encode(Image.new("L", (8, 8), 128), "PNG")).shape == (8, 8, 3)

def test_downscales_large_images_keeping_aspect():
    arr = decode_image(encode(Image.new("RGB", (4000, 3000))))
    assert max(arr.shape[:2]) == MAX_SIDE
    assert arr.shape[:2] == (1200, 1600)

def test_applies_exif_rotation():
    img = Image.new("RGB", (40, 20))  # largura > altura
    exif = img.getexif()
    exif[0x0112] = 6  # Orientation: girar 90° (foto de celular em pé)
    arr = decode_image(encode(img, exif=exif))
    assert arr.shape[:2] == (40, 20)  # agora altura > largura
