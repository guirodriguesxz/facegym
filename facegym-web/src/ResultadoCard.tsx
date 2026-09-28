import { useState, type FormEvent } from 'react'
import { descrever, type RespostaCheckIn } from './resultado'

const TONS = {
  ok: 'border-emerald-500 bg-emerald-500/10 text-emerald-300',
  erro: 'border-rose-500 bg-rose-500/10 text-rose-300',
  aviso: 'border-amber-500 bg-amber-500/10 text-amber-200',
}

export function ResultadoCard({ resposta, onCpf, ocupado }: {
  resposta: RespostaCheckIn
  onCpf: (cpf: string) => void
  ocupado: boolean
}) {
  const d = descrever(resposta)
  const [cpf, setCpf] = useState('')
  const enviar = (e: FormEvent) => { e.preventDefault(); onCpf(cpf) }
  return (
    <div className={`rounded-2xl border-2 p-6 ${TONS[d.tom]}`} role="status" aria-live="polite">
      <p className="text-2xl font-semibold">{d.titulo}</p>
      {d.detalhe && <p className="mt-1 text-lg">{d.detalhe}</p>}
      {d.pedeCpf && (
        <form onSubmit={enviar} className="mt-4 flex gap-2">
          <input value={cpf} onChange={(e) => setCpf(e.target.value)} inputMode="numeric" placeholder="CPF"
            className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-slate-100 outline-none ring-1 ring-slate-700 focus:ring-emerald-500" />
          <button disabled={ocupado || !cpf} className="rounded-lg bg-emerald-500 px-4 py-2 font-medium text-slate-950 disabled:opacity-50">Entrar</button>
        </form>
      )}
    </div>
  )
}
