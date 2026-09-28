from uuid import uuid4
from tests.conftest import RED, png

def test_health_is_public(client):
    assert client.get("/health").json() == {"status": "UP"}

def test_faces_require_internal_key(client):
    r = client.post("/faces/identify", files={"image": ("a.png", png(RED), "image/png")})
    assert r.status_code == 401

def test_wrong_internal_key_is_rejected(client):
    r = client.delete(f"/faces/{uuid4()}", headers={"X-Internal-Key": "errada"})
    assert r.status_code == 401

from tests.conftest import BLUE, GREEN

def upload(color):
    return {"image": ("f.png", png(color), "image/png")}

def test_register_then_identify(client, auth):
    aluno = uuid4()
    assert client.put(f"/faces/{aluno}", files=upload(RED), headers=auth).status_code == 204
    r = client.post("/faces/identify", files=upload(RED), headers=auth)
    assert r.status_code == 200
    assert r.json() == {"alunoId": str(aluno), "score": 1.0}

def test_identify_with_empty_database_returns_null(client, auth):
    r = client.post("/faces/identify", files=upload(RED), headers=auth)
    assert r.json() == {"alunoId": None, "score": None}

def test_identify_without_face_returns_null(client, auth):
    client.put(f"/faces/{uuid4()}", files=upload(RED), headers=auth)
    r = client.post("/faces/identify", files=upload(BLUE), headers=auth)
    assert r.status_code == 200 and r.json() == {"alunoId": None, "score": None}

def test_register_without_face_is_422(client, auth, repo):
    r = client.put(f"/faces/{uuid4()}", files=upload(BLUE), headers=auth)
    assert r.status_code == 422 and r.json()["detail"] == "nenhum rosto encontrado"
    assert repo.rows == {}

def test_non_image_is_422(client, auth):
    r = client.post("/faces/identify", files={"image": ("x.txt", b"oi", "text/plain")}, headers=auth)
    assert r.status_code == 422 and r.json()["detail"] == "imagem inválida"

def test_image_over_5mb_is_413(client, auth):
    big = b"\x00" * (5 * 1024 * 1024 + 1)
    r = client.post("/faces/identify", files={"image": ("big.png", big, "image/png")}, headers=auth)
    assert r.status_code == 413

def test_invalid_aluno_id_is_422(client, auth):
    assert client.put("/faces/nao-e-uuid", files=upload(RED), headers=auth).status_code == 422

def test_delete_removes_and_is_idempotent(client, auth):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files=upload(RED), headers=auth)
    assert client.delete(f"/faces/{aluno}", headers=auth).status_code == 204
    assert client.delete(f"/faces/{aluno}", headers=auth).status_code == 204
    assert client.post("/faces/identify", files=upload(RED), headers=auth).json()["alunoId"] is None

def test_compare_demo_never_writes(client, auth, repo):
    aluno = uuid4()
    client.put(f"/faces/{aluno}", files=upload(GREEN), headers=auth)
    before = dict(repo.rows)
    r = client.post("/faces/compare-demo", files=upload(GREEN), headers=auth)
    assert r.json()["alunoId"] == str(aluno)
    assert repo.rows == before
