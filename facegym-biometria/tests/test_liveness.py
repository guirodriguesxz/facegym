from uuid import uuid4
import numpy as np
import pytest
from app.liveness import vivo, yaw
from tests.conftest import GREEN, RED, RED_PRINTED, RED_TURNED, png, vec

def kps(nariz_x: float) -> np.ndarray:
    # olhos em x=40 e x=60 (distância 20), nariz no meio vertical
    return np.array([[40, 50], [60, 50], [nariz_x, 65], [42, 80], [58, 80]], dtype=np.float32)

def test_yaw_de_frente_e_virado():
    assert yaw(kps(50)) == 0.0
    assert yaw(kps(58)) == pytest.approx(0.4)
    assert yaw(kps(42)) == pytest.approx(-0.4)

def test_foto_plana_girada_nao_muda_o_yaw():
    # um plano girado diante da câmera se comprime por igual na horizontal
    plano = kps(50).copy()
    plano[:, 0] = 50 + (plano[:, 0] - 50) * 0.6
    assert yaw(plano) == pytest.approx(0.0)

def test_vivo_exige_frente_virado_e_mesma_pessoa():
    assert vivo(vec(0), 0.0, vec(0), 0.45, "ESQUERDA")
    assert not vivo(vec(0), 0.0, vec(0), 0.05, "ESQUERDA")   # não virou
    assert not vivo(vec(0), 0.4, vec(0), 0.45, "ESQUERDA")   # a primeira já não estava de frente
    assert not vivo(vec(0), 0.0, vec(1), 0.45, "ESQUERDA")   # outra pessoa na segunda foto

def test_vivo_exige_o_lado_sorteado():
    assert vivo(vec(0), 0.0, vec(0), -0.45, "DIREITA")
    assert not vivo(vec(0), 0.0, vec(0), 0.45, "DIREITA")    # virou para o lado errado
    assert not vivo(vec(0), 0.0, vec(0), 0.45, "CIMA")       # direção desconhecida

def files(frente, virado):
    return {"image": ("a.png", png(frente), "image/png"), "turned": ("b.png", png(virado), "image/png")}

ESQUERDA = {"direction": "ESQUERDA"}

def test_identify_live_aprova_e_identifica(client, auth):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files={"image": ("f.png", png(RED), "image/png")}, headers=auth)
    r = client.post("/faces/identify-live", files=files(RED, RED_TURNED), data=ESQUERDA, headers=auth)
    assert r.json() == {"alunoId": str(aluno), "score": 1.0, "vivo": True}

def test_identify_live_reprovado_nao_revela_o_aluno(client, auth):
    client.put(f"/faces/{uuid4()}", files={"image": ("f.png", png(RED), "image/png")}, headers=auth)
    for frente, virado in [(RED, RED), (RED, GREEN)]:  # não virou / trocou de pessoa
        r = client.post("/faces/identify-live", files=files(frente, virado), data=ESQUERDA, headers=auth)
        assert r.json() == {"alunoId": None, "score": None, "vivo": False}

def test_identify_live_exige_chave(client):
    assert client.post("/faces/identify-live", files=files(RED, RED_TURNED)).status_code == 401

def test_identify_live_lado_errado_reprova(client, auth):
    client.put(f"/faces/{uuid4()}", files={"image": ("f.png", png(RED), "image/png")}, headers=auth)
    r = client.post("/faces/identify-live", files=files(RED, RED_TURNED), data={"direction": "DIREITA"}, headers=auth)
    assert r.json() == {"alunoId": None, "score": None, "vivo": False}

def test_identify_live_reprova_reproducao_mesmo_com_giro_certo(client, auth):
    client.put(f"/faces/{uuid4()}", files={"image": ("f.png", png(RED), "image/png")}, headers=auth)
    r = client.post("/faces/identify-live", files=files(RED_PRINTED, RED_TURNED), data=ESQUERDA, headers=auth)
    assert r.json() == {"alunoId": None, "score": None, "vivo": False}
