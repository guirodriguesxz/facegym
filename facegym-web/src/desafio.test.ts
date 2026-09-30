import { describe, expect, it } from 'vitest'
import { instrucao } from './desafio'

describe('instrucao', () => {
  it('fala do ponto de vista da pessoa, com a seta do lado que ela vê na tela espelhada', () => {
    expect(instrucao('ESQUERDA')).toEqual({ texto: 'Vire o rosto para a esquerda', seta: '←' })
    expect(instrucao('DIREITA')).toEqual({ texto: 'Vire o rosto para a direita', seta: '→' })
  })
})
