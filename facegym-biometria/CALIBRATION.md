# Calibração dos limiares

Medido com `pytest -m slow tests/test_accuracy.py -s` (InsightFace `buffalo_s`, LFW com
`min_faces_per_person=20`, até 5 fotos por pessoa, imagem inteira 250×250).

| Amostra | Valor |
|---|---|
| Pessoas / pares mesma pessoa / pares diferentes | 62 / 620 / 1891 |
| Mesma pessoa — p5 | 0.407 |
| Mesma pessoa — mediana | 0.594 |
| Pessoas diferentes — p99 | 0.177 |
| Pessoas diferentes — máximo | 0.240 |

## Limiares recomendados (padrão no plano 2)

- `LIMIAR_ACEITE = 0.41` — max(p99 diferentes + 0.05, p5 mesma pessoa). Acima disso: identificado.
- `LIMIAR_DUVIDA = 0.18` — p99 diferentes. Entre 0.18 e 0.41: pede confirmação por CPF.

Com esses valores, ~95% dos check-ins da própria pessoa passam direto, e nenhum par de pessoas
diferentes da amostra (máximo 0.240) chega perto do aceite. Os 0.80/0.60 da spec eram
altos demais para este modelo: quase ninguém seria reconhecido.
