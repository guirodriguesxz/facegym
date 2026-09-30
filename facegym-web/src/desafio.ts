export type Lado = 'ESQUERDA' | 'DIREITA'
export type Desafio = { token: string; lado: Lado }
export type FotosComDesafio = { frente: Blob; virada: Blob; desafio: Desafio }

export const SEGUNDOS_DESAFIO = 3

/** O preview é espelhado: a esquerda da pessoa aparece à esquerda da tela, então a seta acompanha. */
export function instrucao(lado: Lado): { texto: string; seta: string } {
  return lado === 'ESQUERDA'
    ? { texto: 'Vire o rosto para a esquerda', seta: '←' }
    : { texto: 'Vire o rosto para a direita', seta: '→' }
}

export class CapturaFalhou extends Error {
  constructor() { super('Não consegui capturar a foto. Tente de novo.') }
}

/**
 * Pede o desafio, fotografa de frente, conta e fotografa virado.
 * Devolve `null` se `cancelado()` ficar verdadeiro no meio (a câmera saiu da tela).
 */
export async function executarDesafio(p: {
  pedir: () => Promise<Desafio>
  capturar: () => Promise<Blob | null>
  esperar: (ms: number) => Promise<unknown>
  onDesafio: (d: Desafio) => void
  onContagem: (c: { lado: Lado; segundos: number } | null) => void
  cancelado: () => boolean
}): Promise<FotosComDesafio | null> {
  const desafio = await p.pedir()
  if (p.cancelado()) return null
  const frente = await p.capturar()
  if (!frente) throw new CapturaFalhou()
  p.onDesafio(desafio)
  try {
    for (let s = SEGUNDOS_DESAFIO; s > 0; s--) {
      p.onContagem({ lado: desafio.lado, segundos: s })
      await p.esperar(1000)
      if (p.cancelado()) return null
    }
    const virada = await p.capturar()
    if (!virada) throw new CapturaFalhou()
    return { frente, virada, desafio }
  } finally {
    p.onContagem(null)
  }
}
