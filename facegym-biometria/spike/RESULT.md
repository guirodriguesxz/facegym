# Spike: buffalo_s em 512 MB

Container `python:3.12-slim` com `-m 512m`, insightface 0.7.3, onnxruntime 1.19.2, det_size 640x640.

| Métrica | Valor | Critério |
|---|---|---|
| Carga do modelo | 5,6 s | — |
| RSS máximo | 313 MB | ≤ 400 MB ✅ |
| Latência p50 (1 rosto, 10 execuções) | 83 ms | ≤ 800 ms ✅ |

Medido num Mac (Apple Silicon, Docker). A CPU do Render free é mais lenta; a margem para 800 ms é ampla.

**Decisão:** seguir com `buffalo_s` e `det_size=(640, 640)`.
