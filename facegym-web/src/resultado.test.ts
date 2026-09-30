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
  it('dúvida, não reconhecido e biometria fora pedem CPF', () => {
    for (const status of ['CONFIRMAR_CPF', 'NAO_RECONHECIDO', 'BIOMETRIA_INDISPONIVEL'] as const) {
      expect(descrever({ status }).pedeCpf).toBe(true)
    }
    expect(descrever({ status: 'BIOMETRIA_INDISPONIVEL' }).titulo).toBe('Reconhecimento indisponível')
  })
  it('liberado com prova de vida ganha selo', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana', vivacidade: true }).selo).toBe('✓ Prova de vida')
  })
  it('aluno fictício liberado sem prova de vida ganha selo de demo; CPF não ganha selo', () => {
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' }, true).selo).toBe('Sem prova de vida (demo)')
    expect(descrever({ status: 'LIBERADO', nome: 'Ana' }).selo).toBeUndefined()
  })
  it('prova de vida reprovada pede nova tentativa, sem nome e sem CPF', () => {
    expect(descrever({ status: 'PROVA_DE_VIDA_REPROVADA', motivo: 'Prova de vida não confirmada' }))
      .toEqual({ titulo: 'Não deu para confirmar', detalhe: 'Prova de vida não confirmada — tente de novo.', tom: 'aviso', pedeCpf: false })
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
