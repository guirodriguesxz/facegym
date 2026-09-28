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

import logging

def test_image_bytes_never_logged(client, auth, caplog):
    marker = png(RED)
    with caplog.at_level(logging.DEBUG):
        client.put(f"/faces/{uuid4()}", files={"image": ("f.png", marker, "image/png")}, headers=auth)
    assert marker[:32].hex() not in caplog.text
    assert "embedding" not in caplog.text.lower()

import threading

def test_unauthenticated_body_is_never_consumed(client):
    consumed = []

    def body():
        for _ in range(64):
            consumed.append(1)
            yield b"\x00" * 65536

    r = client.post("/faces/identify", content=body(),
                    headers={"Content-Type": "multipart/form-data; boundary=x"})
    assert r.status_code == 401
    assert len(consumed) <= 1  # rejeitado antes de ler o corpo

def test_oversized_content_length_is_413_before_parsing(client, auth):
    r = client.post("/faces/identify", content=b"x" * 10, headers={**auth, "Content-Length": str(6 * 1024 * 1024), "Content-Type": "multipart/form-data; boundary=x"})
    assert r.status_code == 413

def test_health_answers_while_inference_is_running(repo):
    import socket, time, httpx, uvicorn
    from app.api import create_app
    from tests.conftest import KEY
    started, release = threading.Event(), threading.Event()

    class SlowEmbedder:
        def embed(self, image_bgr):
            started.set()
            release.wait(5)
            return None

    sock = socket.socket(); sock.bind(("127.0.0.1", 0)); port = sock.getsockname()[1]; sock.close()
    server = uvicorn.Server(uvicorn.Config(create_app(SlowEmbedder(), repo, KEY), port=port, log_level="error"))
    threading.Thread(target=server.run, daemon=True).start()
    base = f"http://127.0.0.1:{port}"
    for _ in range(100):
        try:
            httpx.get(base + "/health"); break
        except httpx.ConnectError:
            time.sleep(0.05)
    t = threading.Thread(target=lambda: httpx.post(base + "/faces/identify", headers={"X-Internal-Key": KEY},
                                                     files={"image": ("f.png", png(RED), "image/png")}, timeout=10))
    t.start()
    try:
        assert started.wait(5)
        r = httpx.get(base + "/health", timeout=1)
        assert r.status_code == 200
    finally:
        release.set(); t.join(5); server.should_exit = True
