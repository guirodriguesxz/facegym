import resource, time, urllib.request
import numpy as np
import cv2
from insightface.app import FaceAnalysis

URL = "https://upload.wikimedia.org/wikipedia/commons/a/a0/Pierre-Person.jpg"

def rss_mb() -> float:
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024  # Linux: KB

t0 = time.perf_counter()
app = FaceAnalysis(name="buffalo_s", allowed_modules=["detection", "recognition"],
                   providers=["CPUExecutionProvider"])
app.prepare(ctx_id=-1, det_size=(640, 640))
print(f"load: {time.perf_counter() - t0:.1f}s, rss: {rss_mb():.0f} MB")

req = urllib.request.Request(URL, headers={"User-Agent": "facegym-spike/0.1"})
img = cv2.imdecode(np.frombuffer(urllib.request.urlopen(req).read(), np.uint8), cv2.IMREAD_COLOR)
times = []
for _ in range(10):
    t = time.perf_counter()
    faces = app.get(img)
    times.append(time.perf_counter() - t)
print(f"faces: {len(faces)}, p50: {sorted(times)[5]*1000:.0f} ms, max rss: {rss_mb():.0f} MB")
