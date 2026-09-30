import hmac
from uuid import UUID
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Response, UploadFile
from fastapi.responses import JSONResponse
from app.antispoof import REAL_MIN, AntiSpoof
from app.embedder import Embedder
from app.images import InvalidImage, decode_image
from app.liveness import vivo
from app.repository import FaceRepository

MAX_BYTES = 5 * 1024 * 1024
# Margem para os cabeçalhos do multipart em volta da imagem.
MAX_BODY = MAX_BYTES + 512 * 1024
# A prova de vida manda duas imagens.
MAX_BODY_LIVE = 2 * MAX_BYTES + 512 * 1024

def create_app(embedder: Embedder, repo: FaceRepository, internal_key: str, anti_spoof: AntiSpoof) -> FastAPI:
    app = FastAPI(title="FaceGym Biometria")

    def key_ok(value: str) -> bool:
        return hmac.compare_digest(value.encode(), internal_key.encode())

    @app.middleware("http")
    async def reject_before_body(request, call_next):
        # O FastAPI lê o multipart inteiro antes de rodar as dependências; sem
        # isto, um corpo gigante sem chave seria recebido antes do 401.
        if request.url.path.startswith("/faces"):
            if not key_ok(request.headers.get("x-internal-key", "")):
                return JSONResponse({"detail": "não autorizado"}, status_code=401)
            length = request.headers.get("content-length")
            limite = MAX_BODY_LIVE if request.url.path == "/faces/identify-live" else MAX_BODY
            if length is not None and length.isdigit() and int(length) > limite:
                return JSONResponse({"detail": "imagem maior que 5 MB"}, status_code=413)
        return await call_next(request)

    def require_key(x_internal_key: str = Header(default="")) -> None:
        if not key_ok(x_internal_key):
            raise HTTPException(status_code=401, detail="não autorizado")

    def read_image(image: UploadFile):
        data = image.file.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise HTTPException(status_code=413, detail="imagem maior que 5 MB")
        try:
            return decode_image(data)
        except InvalidImage:
            raise HTTPException(status_code=422, detail="imagem inválida")

    def read_embedding(image: UploadFile):
        return embedder.embed(read_image(image))

    def identify_impl(image: UploadFile) -> dict:
        embedding = read_embedding(image)
        match = repo.nearest(embedding) if embedding is not None else None
        if match is None:
            return {"alunoId": None, "score": None}
        aluno_id, score = match
        return {"alunoId": str(aluno_id), "score": round(score, 4)}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.put("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def register(aluno_id: UUID, image: UploadFile = File(...)):
        embedding = read_embedding(image)
        if embedding is None:
            raise HTTPException(status_code=422, detail="nenhum rosto encontrado")
        repo.upsert(aluno_id, embedding)
        return Response(status_code=204)

    @app.post("/faces/identify", dependencies=[Depends(require_key)])
    def identify(image: UploadFile = File(...)):
        return identify_impl(image)

    @app.post("/faces/identify-live", dependencies=[Depends(require_key)])
    def identify_live(image: UploadFile = File(...), turned: UploadFile = File(...), direction: str = Form(...)):
        # image: de frente; turned: rosto virado para o lado sorteado (direction). Reprovado não revela de quem é o rosto.
        img_frente, img_virado = read_image(image), read_image(turned)
        frente, virado = embedder.analyze(img_frente), embedder.analyze(img_virado)
        if (frente is None or virado is None
                or not vivo(frente[0], frente[1], virado[0], virado[1], direction)
                # anti-spoofing passivo nas duas fotos: barra papel e tela mesmo que o giro engane
                or anti_spoof.real_score(img_frente, frente[2]) < REAL_MIN
                or anti_spoof.real_score(img_virado, virado[2]) < REAL_MIN):
            return {"alunoId": None, "score": None, "vivo": False}
        match = repo.nearest(frente[0])
        if match is None:
            return {"alunoId": None, "score": None, "vivo": True}
        aluno_id, score = match
        return {"alunoId": str(aluno_id), "score": round(score, 4), "vivo": True}

    @app.post("/faces/compare-demo", dependencies=[Depends(require_key)])
    def compare_demo(image: UploadFile = File(...)):
        # Mesmo cálculo do identify; rota separada para o plano 2 poder
        # aplicar limites próprios à câmera de visitantes.
        return identify_impl(image)

    @app.delete("/faces/{aluno_id}", status_code=204, dependencies=[Depends(require_key)])
    def delete_face(aluno_id: UUID):
        repo.delete(aluno_id)
        return Response(status_code=204)

    return app
