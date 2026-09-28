import itertools
import numpy as np
import pytest

pytestmark = pytest.mark.slow

@pytest.fixture(scope="module")
def embeddings():
    from sklearn.datasets import fetch_lfw_people
    from app.embedder import InsightFaceEmbedder
    lfw = fetch_lfw_people(min_faces_per_person=20, resize=1.0, color=True, slice_=None)
    embedder = InsightFaceEmbedder()
    by_person: dict[int, list[np.ndarray]] = {}
    for img, target in zip(lfw.images, lfw.target):
        if len(by_person.get(target, [])) >= 5:
            continue
        bgr = np.ascontiguousarray((img[:, :, ::-1] * 255).astype(np.uint8))
        e = embedder.embed(bgr)
        if e is not None:
            by_person.setdefault(target, []).append(e)
    return {p: v for p, v in by_person.items() if len(v) >= 2}

def scores(embeddings):
    same = [float(a @ b) for v in embeddings.values() for a, b in itertools.combinations(v, 2)]
    people = list(embeddings.values())
    diff = [float(a[0] @ b[0]) for a, b in itertools.combinations(people, 2)]
    return np.array(same), np.array(diff)

def test_same_person_scores_higher_than_different_people(embeddings):
    same, diff = scores(embeddings)
    print(f"\npessoas: {len(embeddings)}, pares mesma pessoa: {len(same)}, pares diferentes: {len(diff)}")
    print(f"mesma pessoa: p5={np.percentile(same, 5):.3f} mediana={np.median(same):.3f}")
    print(f"pessoas diferentes: p99={np.percentile(diff, 99):.3f} max={diff.max():.3f}")
    # O modelo precisa separar bem: quase toda comparação da mesma pessoa acima
    # da pior comparação entre pessoas diferentes.
    assert np.percentile(same, 5) > np.percentile(diff, 99)
