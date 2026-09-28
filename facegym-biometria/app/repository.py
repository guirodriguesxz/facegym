from typing import Protocol
from uuid import UUID
import numpy as np
import psycopg
from pgvector.psycopg import register_vector

class FaceRepository(Protocol):
    def upsert(self, aluno_id: UUID, embedding: np.ndarray) -> None: ...
    def nearest(self, embedding: np.ndarray) -> tuple[UUID, float] | None: ...
    def delete(self, aluno_id: UUID) -> None: ...

class PgFaceRepository:
    def __init__(self, database_url: str):
        self._url = database_url

    def _connect(self) -> psycopg.Connection:
        conn = psycopg.connect(self._url, autocommit=True)
        register_vector(conn)
        return conn

    def init_schema(self) -> None:
        with psycopg.connect(self._url, autocommit=True) as conn:
            conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        with self._connect() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS face_embedding (
                    aluno_id  uuid PRIMARY KEY,
                    embedding vector(512) NOT NULL,
                    criado_em timestamptz NOT NULL DEFAULT now()
                )""")

    def upsert(self, aluno_id: UUID, embedding: np.ndarray) -> None:
        with self._connect() as conn:
            conn.execute(
                """INSERT INTO face_embedding (aluno_id, embedding) VALUES (%s, %s)
                   ON CONFLICT (aluno_id) DO UPDATE SET embedding = EXCLUDED.embedding, criado_em = now()""",
                (aluno_id, embedding))

    def nearest(self, embedding: np.ndarray) -> tuple[UUID, float] | None:
        with self._connect() as conn:
            row = conn.execute(
                """SELECT aluno_id, 1 - (embedding <=> %s) AS score
                   FROM face_embedding ORDER BY embedding <=> %s LIMIT 1""",
                (embedding, embedding)).fetchone()
        return (row[0], float(row[1])) if row else None

    def delete(self, aluno_id: UUID) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM face_embedding WHERE aluno_id = %s", (aluno_id,))

    # usados só por testes
    def clear(self) -> None:
        with self._connect() as conn:
            conn.execute("TRUNCATE face_embedding")

    def count(self) -> int:
        with self._connect() as conn:
            return conn.execute("SELECT count(*) FROM face_embedding").fetchone()[0]
