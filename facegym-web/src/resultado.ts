export type RespostaCheckIn = {
  status: 'LIBERADO' | 'NEGADO' | 'CONFIRMAR_CPF' | 'NAO_RECONHECIDO' | 'BIOMETRIA_INDISPONIVEL'
  nome?: string
  motivo?: string
  token?: string
}

export type Descricao = { titulo: string; detalhe?: string; tom: 'ok' | 'erro' | 'aviso'; pedeCpf: boolean }

export function descrever(r: RespostaCheckIn): Descricao {
  switch (r.status) {
    case 'LIBERADO':
      return { titulo: `Bem-vinda(o), ${r.nome}!`, tom: 'ok', pedeCpf: false }
    case 'NEGADO':
      return { titulo: `Acesso negado, ${r.nome}`, detalhe: r.motivo, tom: 'erro', pedeCpf: false }
    case 'CONFIRMAR_CPF':
      return { titulo: 'Quase lá', detalhe: 'Confirme seu CPF para entrar.', tom: 'aviso', pedeCpf: true }
    case 'NAO_RECONHECIDO':
      return { titulo: 'Rosto não reconhecido', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
    case 'BIOMETRIA_INDISPONIVEL':
      return { titulo: 'Reconhecimento indisponível', detalhe: 'Entre com seu CPF.', tom: 'aviso', pedeCpf: true }
  }
}

/** Máscara do visor da catraca: 000.000.000-00, no máximo 11 dígitos. */
export function formatarCpf(digitos: string): string {
  const d = digitos.replace(/\D/g, '').slice(0, 11)
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2')
}
