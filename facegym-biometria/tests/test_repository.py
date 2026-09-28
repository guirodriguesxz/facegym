from uuid import uuid4
import numpy as np
import pytest
from testcontainers.postgres import PostgresContainer
from app.repository import PgFaceRepository
from tests.conftest import vec

@pytest.fixture(scope="module")
def database_url():
    with PostgresContainer("pgvector/pgvector:pg16", driver=None) as pg:
        yield pg.get_connection_url()

@pytest.fixture
def repo(database_url):
    r = PgFaceRepository(database_url)
    r.init_schema()
    r.clear()
    return r

def test_nearest_on_empty_table_returns_none(repo):
    assert repo.nearest(vec(0)) is None

def test_nearest_returns_closest_with_cosine_similarity(repo):
    a, b = uuid4(), uuid4()
    repo.upsert(a, vec(0))
    repo.upsert(b, vec(1))
    probe = (vec(0) * 0.9 + vec(1) * 0.1)
    probe /= np.linalg.norm(probe)
    aluno_id, score = repo.nearest(probe)
    assert aluno_id == a
    assert score == pytest.approx(float(probe @ vec(0)), abs=1e-4)

def test_upsert_replaces_instead_of_duplicating(repo):
    a = uuid4()
    repo.upsert(a, vec(0))
    repo.upsert(a, vec(1))
    assert repo.count() == 1
    assert repo.nearest(vec(1)) == (a, pytest.approx(1.0, abs=1e-4))

def test_delete_is_idempotent(repo):
    a = uuid4()
    repo.upsert(a, vec(0))
    repo.delete(a)
    repo.delete(a)
    assert repo.nearest(vec(0)) is None
