import numpy as np
import pytest
from app.liveness import giro

def pontos(nariz_x: float, olho_a=(40.0, 50.0), olho_b=(80.0, 50.0)) -> np.ndarray:
    """5 pontos do RetinaFace na ordem da imagem: olho esq., olho dir., nariz, boca esq., boca dir."""
    return np.array([olho_a, olho_b, (nariz_x, 70.0), (45.0, 90.0), (75.0, 90.0)], dtype=np.float32)

def test_de_frente_e_zero():
    assert giro(pontos(60.0)) == pytest.approx(0.0)

def test_virar_para_a_esquerda_da_pessoa_e_positivo():
    # foto sem espelhamento: a esquerda da pessoa fica à direita da imagem
    assert giro(pontos(76.0)) == pytest.approx(0.4)

def test_virar_para_a_direita_da_pessoa_e_negativo():
    assert giro(pontos(44.0)) == pytest.approx(-0.4)

def test_olhos_colados_nao_divide_por_zero():
    assert np.isfinite(giro(pontos(61.0, olho_a=(60.0, 50.0), olho_b=(60.0, 50.0))))
