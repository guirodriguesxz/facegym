from pathlib import Path
import numpy as np
import pytest
from app.images import decode_image
from app.liveness import giro

pytestmark = pytest.mark.slow

FOTOS = sorted((Path(__file__).parents[2] / "scripts" / "demo-fotos").glob("*.jpg"))

@pytest.fixture(scope="module")
def embedder():
    from app.embedder import InsightFaceEmbedder
    return InsightFaceEmbedder()

@pytest.mark.parametrize("foto", FOTOS, ids=lambda p: p.stem)
def test_retrato_frontal_mede_de_frente_e_espelho_inverte_o_sinal(embedder, foto):
    img = decode_image(foto.read_bytes())
    rosto = embedder.analyze(img)
    espelho = embedder.analyze(np.ascontiguousarray(img[:, ::-1]))
    assert rosto is not None and espelho is not None
    g, ge = giro(rosto.kps), giro(espelho.kps)
    print(f"\n{foto.stem}: giro={g:+.3f} espelhado={ge:+.3f}")
    assert abs(g) < 0.15
    assert ge == pytest.approx(-g, abs=0.05)
