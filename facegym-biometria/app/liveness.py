"""Prova de vida por desafio: uma foto de frente e outra com o rosto virado.

O giro é estimado pelos 5 pontos que o RetinaFace já devolve (olhos, nariz, cantos da boca):
com o rosto virado, o nariz se afasta do meio dos olhos. Uma foto impressa ou uma tela
inclinada diante da câmera não muda essa proporção, porque um plano girado se comprime por
igual; só um rosto em 3D desloca o nariz em relação aos olhos.
"""
import numpy as np

# |yaw| até aqui conta como "de frente"; a partir de VIRADO_MIN conta como "virado".
FRENTE_MAX = 0.15
VIRADO_MIN = 0.30
# Rosto virado baixa a similaridade; abaixo disto não é a mesma pessoa nas duas fotos.
MESMA_PESSOA_MIN = 0.30

# Lado sorteado pelo servidor, do ponto de vista da pessoa. A foto chega sem espelhar (o espelho
# do totem é só CSS): virar para a própria esquerda leva o nariz para a direita da imagem (yaw > 0).
SINAL = {"ESQUERDA": 1.0, "DIREITA": -1.0}


def yaw(kps: np.ndarray) -> float:
    """Deslocamento horizontal do nariz em relação ao meio dos olhos, em distâncias entre olhos."""
    olho_esq, olho_dir, nariz = kps[0], kps[1], kps[2]
    distancia = float(np.linalg.norm(olho_dir - olho_esq))
    if distancia < 1e-6:
        return 0.0
    meio = (olho_esq + olho_dir) / 2
    return float((nariz[0] - meio[0]) / distancia)


def vivo(emb_frente: np.ndarray, yaw_frente: float, emb_virado: np.ndarray, yaw_virado: float,
         direcao: str) -> bool:
    """direcao: ESQUERDA ou DIREITA; o rosto precisa ter girado para esse lado."""
    return (direcao in SINAL
            and abs(yaw_frente) <= FRENTE_MAX
            and SINAL[direcao] * yaw_virado >= VIRADO_MIN
            and float(emb_frente @ emb_virado) >= MESMA_PESSOA_MIN)
