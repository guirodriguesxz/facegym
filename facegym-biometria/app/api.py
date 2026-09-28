import hmac
from uuid import UUID
from fastapi import Depends, FastAPI, File, Header, HTTPException, Response, UploadFile
from app.embedder import Embedder
from app.images import InvalidImage, decode_image
from app.repository import FaceRepository

MAX_BYTES = 5 * 1024 * 1024

def create_app(embedder: Embedder, repo: FaceRepository, internal_key: str) -> FastAPI:
    app = FastAPI(title="FaceGym Biometria")

    def require_key(x_internal_key: str = Header(default="")) -> None:
        if not hmac.compare_digest(x_internal_key.encode(), internal_key.encode()):
            raise HTTPException(status_code=401, detail="não autorizado")

    async def read_embedding(image: UploadFile):
        data = await image.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise HTTPException(status_code=413, detail="imagem maior que 5 MB")
        try:
            return embedder.embed(decode_image(data))
        except InvalidImage:
            raise HTTPException(status_code=422, detail="imagem inválida")

    async def identify_impl(image: UploadFile) -> dict:
        embedding = await read_embedding(image)
        match = repo.nearest(embedding) if embedding is not None else None
        if match is None:
            return {"alunoId": None, "score": None}
        aluno_id, score = match
        return {"alunoId": str(aluno_id), "score": round(score, 4)}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.put("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    async def register(aluno_id: UUID, image: UploadFile = File(...)):
        embedding = await read_embedding(image)
        if embedding is None:
            raise HTTPException(status_code=422, detail="nenhum rosto encontrado")
        repo.upsert(aluno_id, embedding)
        return Response(status_code=204)

    @app.post("/faces/identify", dependencies=[Depends(require_key)])
    async def identify(image: UploadFile = File(...)):
        return await identify_impl(image)

    @app.post("/faces/compare-demo", dependencies=[Depends(require_key)])
    async def compare_demo(image: UploadFile = File(...)):
        # Mesmo cálculo do identify; rota separada para o plano 2 poder
        # aplicar limites próprios à câmera de visitantes.
        return await identify_impl(image)

    @app.delete("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def delete_face(aluno_id: UUID):
        repo.delete(aluno_id)
        return Response(status_code=204)

    return app
