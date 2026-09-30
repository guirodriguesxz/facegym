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
