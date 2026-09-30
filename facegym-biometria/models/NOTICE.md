# Modelos anti-spoofing

`minifasnet_v2_2.7.onnx` e `minifasnet_v1se_4.0.onnx` são conversões para ONNX dos pesos
`2.7_80x80_MiniFASNetV2.pth` e `4_0_0_80x80_MiniFASNetV1SE.pth` do projeto
[Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)
(Minivision), sob a licença Apache-2.0 (`LICENSE-Silent-Face-Anti-Spoofing`).

Conversão (2026-09-29): `torch.onnx.export`, opset 13, entrada `input` 1×3×80×80 (BGR, 0–255, sem
normalização), saída `logits` (3 classes; 1 = rosto real). Diferença máxima entre PyTorch e ONNX
nas probabilidades: 1,2e-7.

| Arquivo de origem | git blob SHA-1 no repositório original |
|---|---|
| 2.7_80x80_MiniFASNetV2.pth | 47c4af2023fb072c0f5b0e0ade4824053a0558e1 |
| 4_0_0_80x80_MiniFASNetV1SE.pth | 55a25b316ef33ced3925687007a31a5e306990db |

O SHA-256 de cada ONNX é conferido ao carregar (`app/antispoof.py`).
