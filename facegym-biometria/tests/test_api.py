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
