import { describe, expect, it } from 'vitest'
import { instrucao } from './desafio'

describe('instrucao', () => {
  it('fala do ponto de vista da pessoa, com a seta do lado que ela vê na tela espelhada', () => {
    expect(instrucao('ESQUERDA')).toEqual({ texto: 'Vire o rosto para a esquerda', seta: '←' })
    expect(instrucao('DIREITA')).toEqual({ texto: 'Vire o rosto para a direita', seta: '→' })
  })
})

import { CapturaFalhou, executarDesafio } from './desafio'

function passos(opts: { fotos: (Blob | null)[]; cancelarApos?: number }) {
  const eventos: string[] = []
  const fotos = [...opts.fotos]
  let esperas = 0
  return {
    eventos,
    capturas: () => opts.fotos.length - fotos.length,
    p: {
      pedir: async () => ({ token: 't', lado: 'ESQUERDA' as const }),
      capturar: async () => fotos.shift() ?? null,
      esperar: async () => { esperas++ },
      onDesafio: () => { eventos.push('desafio') },
      onContagem: (c: { segundos: number } | null) => { eventos.push(c ? String(c.segundos) : 'fim') },
      cancelado: () => opts.cancelarApos !== undefined && esperas >= opts.cancelarApos,
    },
  }
}

const foto = new Blob(['x'])

describe('executarDesafio', () => {
  it('fotografa de frente, conta 3-2-1 e fotografa virado', async () => {
    const t = passos({ fotos: [foto, foto] })
    const r = await executarDesafio(t.p)
    expect(r).toEqual({ frente: foto, virada: foto, desafio: { token: 't', lado: 'ESQUERDA' } })
    expect(t.eventos).toEqual(['desafio', '3', '2', '1', 'fim'])
  })
  it('falha na foto virada vira erro e limpa a contagem (o totem não fica travado)', async () => {
    const t = passos({ fotos: [foto, null] })
    await expect(executarDesafio(t.p)).rejects.toBeInstanceOf(CapturaFalhou)
    expect(t.eventos.at(-1)).toBe('fim')
  })
  it('falha na foto de frente vira erro antes de mostrar o desafio', async () => {
    const t = passos({ fotos: [null] })
    await expect(executarDesafio(t.p)).rejects.toBeInstanceOf(CapturaFalhou)
    expect(t.eventos).toEqual([])
  })
  it('cancelado no meio da contagem não fotografa virado nem devolve fotos', async () => {
    const t = passos({ fotos: [foto, foto], cancelarApos: 1 })
    expect(await executarDesafio(t.p)).toBeNull()
    expect(t.capturas()).toBe(1)
    expect(t.eventos.at(-1)).toBe('fim')
  })
})
