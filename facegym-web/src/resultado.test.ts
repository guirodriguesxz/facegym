import { describe, expect, it } from 'vitest'
import { descrever } from './resultado'

describe('descrever', () => {
  it('liberado mostra boas-vindas', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' })).toEqual({ titulo: 'Bem-vinda(o), Ana!', tom: 'ok', pedeCpf: false })
  })
  it('negado mostra o motivo', () => {
    expect(descrever({ status: 'NEGADO', nome: 'Bruno', motivo: 'Plano vencido ou inexistente' }))
      .toEqual({ titulo: 'Acesso negado, Bruno', detalhe: 'Plano vencido ou inexistente', tom: 'erro', pedeCpf: false })
  })
  it('dúvida, não reconhecido e biometria fora pedem CPF', () => {
    for (const status of ['CONFIRMAR_CPF', 'NAO_RECONHECIDO', 'BIOMETRIA_INDISPONIVEL'] as const) {
      expect(descrever({ status }).pedeCpf).toBe(true)
    }
    expect(descrever({ status: 'BIOMETRIA_INDISPONIVEL' }).titulo).toBe('Reconhecimento indisponível')
  })
})
