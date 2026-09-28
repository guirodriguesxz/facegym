import { useEffect, useState } from 'react'
import { ApiError, apiForm, apiJson } from './api'
import { Camera } from './Camera'

type Visitante = { id: string; nome: string; cpf: string; expiraEm: string }

export function TesteComVoce({ onCheckIn, ocupado }: { onCheckIn: (foto: Blob) => void; ocupado: boolean }) {
  const [aberto, setAberto] = useState(false)
  const [consentiu, setConsentiu] = useState(false)
  const [visitante, setVisitante] = useState<Visitante | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [agora, setAgora] = useState(Date.now())

  useEffect(() => {
    if (!visitante) return
    const t = setInterval(() => setAgora(Date.now()), 1000)
    return () => clearInterval(t)
  }, [visitante])

  const restante = visitante ? Math.max(0, new Date(visitante.expiraEm).getTime() - agora) : 0
  useEffect(() => { if (visitante && restante === 0) setVisitante(null) }, [visitante, restante])

  async function cadastrar(foto: Blob) {
    setCarregando(true); setErro(null)
    try {
      const form = new FormData()
      form.append('foto', foto, 'selfie.jpg')
      form.append('consentimento', 'true')
      setVisitante(await apiForm<Visitante>('/demo/visitantes', form))
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Erro inesperado')
    } finally { setCarregando(false) }
  }

  async function apagar() {
    if (!visitante) return
    await apiJson(`/demo/visitantes/${visitante.id}`, { method: 'DELETE' }).catch(() => {})
    setVisitante(null); setConsentiu(false)
  }

  const mmss = `${Math.floor(restante / 60000)}:${String(Math.floor(restante / 1000) % 60).padStart(2, '0')}`

  return (
    <section className="mt-10 rounded-2xl bg-slate-900 p-6 ring-1 ring-slate-800">
      <h2 className="text-lg font-semibold">Teste com você</h2>
      <p className="mt-1 text-sm text-slate-400">
        Cadastre seu rosto por 10 minutos e faça o check-in de verdade. Guardamos só um vetor numérico do rosto,
        nunca a foto, e tudo é apagado automaticamente.
      </p>

      {!aberto && <button onClick={() => setAberto(true)} className="mt-4 rounded-lg bg-slate-100 px-4 py-2 font-medium text-slate-950">Quero testar</button>}

      {aberto && !visitante && (
        <div className="mt-4 space-y-4">
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={consentiu} onChange={(e) => setConsentiu(e.target.checked)} className="mt-1" />
            <span>Autorizo o uso da minha biometria facial só para este teste, com exclusão automática em 10 minutos.</span>
          </label>
          {consentiu && <Camera rotulo={carregando ? 'Cadastrando…' : '1. Cadastrar meu rosto'} onFoto={cadastrar} desabilitado={carregando} />}
          {erro && <p className="rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}
        </div>
      )}

      {visitante && (
        <div className="mt-4 space-y-4">
          <p className="text-sm">
            Você é <strong>{visitante.nome}</strong> (CPF fictício {visitante.cpf}). Seus dados somem em <strong>{mmss}</strong>.
          </p>
          <Camera rotulo="2. Fazer check-in" onFoto={onCheckIn} desabilitado={ocupado} />
          <button onClick={apagar} className="text-sm text-rose-300 underline">Apagar meus dados agora</button>
        </div>
      )}
    </section>
  )
}
