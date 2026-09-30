import numpy as np

def giro(kps: np.ndarray) -> float:
    """Giro horizontal do rosto pelos 5 pontos do RetinaFace (olho esq., olho dir., nariz, boca esq.,
    boca dir., na ordem da imagem). 0 = de frente; positivo = a pessoa virou para a esquerda dela.

    A foto chega sem espelhamento: a esquerda da pessoa fica à direita da imagem, então virar
    para a esquerda leva o nariz para x maior.
    """
    olho_a, olho_b, nariz = kps[0], kps[1], kps[2]
    meio_x = (olho_a[0] + olho_b[0]) / 2
    distancia = max(abs(float(olho_b[0] - olho_a[0])), 1.0)  # perfil total: olhos quase colados
    return float((nariz[0] - meio_x) / distancia)
