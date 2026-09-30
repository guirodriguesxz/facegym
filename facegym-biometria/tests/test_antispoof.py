import numpy as np
import pytest
from app.antispoof import LADO, recortar

def test_recorte_tem_o_tamanho_da_rede_e_nao_sai_da_imagem():
    img = np.zeros((480, 640, 3), dtype=np.uint8)
    for bbox in ([300, 200, 380, 300], [0, 0, 50, 60], [600, 440, 639, 479]):
        assert recortar(img, np.array(bbox, dtype=np.float32), 4.0).shape == (LADO, LADO, 3)

def test_recorte_centraliza_o_rosto():
    img = np.zeros((400, 400, 3), dtype=np.uint8)
    img[190:210, 190:210] = 255  # "rosto" no centro
    patch = recortar(img, np.array([180, 180, 220, 220], dtype=np.float32), 2.7)
    ys, xs = np.nonzero(patch[:, :, 0])
    assert abs(xs.mean() - LADO / 2) < 3 and abs(ys.mean() - LADO / 2) < 3

@pytest.mark.slow
def test_modelos_reais_carregam_e_conferem_sha():
    from app.antispoof import MiniFASNetAntiSpoof
    score = MiniFASNetAntiSpoof().real_score(np.full((240, 240, 3), 128, np.uint8), np.array([80, 80, 160, 160]))
    assert 0.0 <= score <= 1.0
