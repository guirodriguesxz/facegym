export type RespostaCheckIn = {
  status: 'LIBERADO' | 'NEGADO' | 'CONFIRMAR_CPF' | 'NAO_RECONHECIDO' | 'BIOMETRIA_INDISPONIVEL' | 'PROVA_DE_VIDA_REPROVADA'
  nome?: string
  motivo?: string
  token?: string
  vivacidade?: boolean
}

export type Descricao = { titulo: string; detalhe?: string; tom: 'ok' | 'erro' | 'aviso'; pedeCpf: boolean; selo?: string }

/** `demo`: check-in de aluno fictício, que usa foto única. */
export function descrever(r: RespostaCheckIn, demo = false): Descricao {
  switch (r.status) {
    case 'LIBERADO': {
      const selo = r.vivacidade ? '✓ Prova de vida' : demo ? 'Sem prova de vida (demo)' : undefined
      return { titulo: `Bem-vinda(o), ${r.nome}!`, tom: 'ok', pedeCpf: false, ...(selo && { selo }) }
    }
    case 'NEGADO':
      return { titulo: `Acesso negado, ${r.nome}`, detalhe: r.motivo, tom: 'erro', pedeCpf: false }
    case 'CONFIRMAR_CPF':
      return { titulo: 'Quase lá', detalhe: 'Confirme seu CPF para entrar.', tom: 'aviso', pedeCpf: true }
    case 'NAO_RECONHECIDO':
      return { titulo: 'Rosto não reconhecido', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'BIOMETRIA_INDISPONIVEL':
      return { titulo: 'Reconhecimento indisponível', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'PROVA_DE_VIDA_REPROVADA':
      return { titulo: 'Não deu para confirmar', detalhe: `${r.motivo ?? 'Prova de vida não confirmada'} — tente de novo.`, tom: 'aviso', pedeCpf: false }
  }
}

/** Máscara do visor da catraca: 000.000.000-00, no máximo 11 dígitos. */
export function formatarCpf(digitos: string): string {
  const d = digitos.replace(/\D/g, '').slice(0, 11)
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2')
}
