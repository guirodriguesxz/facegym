import hmac
from fastapi import Depends, FastAPI, Header, HTTPException
from app.embedder import Embedder
from app.repository import FaceRepository

def create_app(embedder: Embedder, repo: FaceRepository, internal_key: str) -> FastAPI:
    app = FastAPI(title="FaceGym Biometria")

    def require_key(x_internal_key: str = Header(default="")) -> None:
        if not hmac.compare_digest(x_internal_key.encode(), internal_key.encode()):
            raise HTTPException(status_code=401, detail="não autorizado")

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.delete("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def delete_face(aluno_id: str):
        raise HTTPException(status_code=501)

    @app.post("/faces/identify", dependencies=[Depends(require_key)])
    def identify():
        raise HTTPException(status_code=501)

    return app
