import { describe, expect, it } from 'vitest'
import { descrever, formatarCpf } from './resultado'

describe('descrever', () => {
  it('liberado mostra boas-vindas', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' })).toEqual({ titulo: 'Bem-vinda(o), Ana!', tom: 'ok', pedeCpf: false })
  })
  it('negado mostra o motivo', () => {
    expect(descrever({ status: 'NEGADO', nome: 'Bruno', motivo: 'Plano vencido ou inexistente' }))
      .toEqual({ titulo: 'Acesso negado, Bruno', detalhe: 'Plano vencido ou inexistente', tom: 'erro', pedeCpf: false })
  })
  it('check-in só por CPF chega sem nome', () => {
    expect(descrever({ status: 'LIBERADO' }).titulo).toBe('Bem-vinda(o)!')
    expect(descrever({ status: 'NEGADO', motivo: 'Procure a recepção' }).titulo).toBe('Acesso negado')
  })
  it('dúvida, não reconhecido e biometria fora pedem CPF', () => {
    for (const status of ['CONFIRMAR_CPF', 'NAO_RECONHECIDO', 'BIOMETRIA_INDISPONIVEL'] as const) {
      expect(descrever({ status }).pedeCpf).toBe(true)
    }
    expect(descrever({ status: 'BIOMETRIA_INDISPONIVEL' }).titulo).toBe('Reconhecimento indisponível')
  })
})

describe('formatarCpf', () => {
  it('formata enquanto digita e ignora o que passa de 11 dígitos', () => {
    expect(formatarCpf('')).toBe('')
    expect(formatarCpf('529')).toBe('529')
    expect(formatarCpf('5299822')).toBe('529.982.2')
    expect(formatarCpf('529982247251')).toBe('529.982.247-25')
  })
})
