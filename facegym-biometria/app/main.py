import logging
from app.antispoof import MiniFASNetAntiSpoof
from app.api import create_app
from app.config import Settings
from app.embedder import InsightFaceEmbedder
from app.repository import PgFaceRepository

# Só níveis e mensagens próprias; nunca logar corpo de requisição (imagem).
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

settings = Settings.from_env()
repo = PgFaceRepository(settings.database_url)
repo.init_schema()
app = create_app(InsightFaceEmbedder(), repo, settings.internal_key, MiniFASNetAntiSpoof())
