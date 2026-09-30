import { useEffect, useState } from 'react'
import { ApiError, apiForm, apiJson } from './api'
import { Camera } from './Camera'

export type Visitante = { id: string; nome: string; cpf: string; expiraEm: string; segredo: string }

/** Recepção: cadastra o rosto do visitante por 10 minutos. O check-in acontece na catraca. */
export function TesteComVoce({ visitante, onVisitante, onApagado, ocupado }: {
  visitante: Visitante | null
  onVisitante: (v: Visitante | null) => void
  onApagado: () => void
  ocupado: boolean
}) {
  const [aberto, setAberto] = useState(false)
  const [consentiu, setConsentiu] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [apagado, setApagado] = useState(false)
  const [apagando, setApagando] = useState(false)
  const [agora, setAgora] = useState(Date.now())

  useEffect(() => {
    if (!visitante) return
    const t = setInterval(() => setAgora(Date.now()), 1000)
    return () => clearInterval(t)
  }, [visitante])

  const restante = visitante ? Math.max(0, new Date(visitante.expiraEm).getTime() - agora) : 0
  useEffect(() => { if (visitante && restante === 0) onVisitante(null) }, [visitante, restante, onVisitante])

  async function cadastrar(foto: Blob) {
    setCarregando(true); setErro(null)
    try {
      const form = new FormData()
      form.append('foto', foto, 'selfie.jpg')
      form.append('consentimento', 'true')
      onVisitante(await apiForm<Visitante>('/demo/visitantes', form))
      setApagado(false)
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Erro inesperado')
    } finally { setCarregando(false) }
  }

  async function apagar() {
    if (!visitante) return
    setErro(null); setApagando(true)
    try {
      await apiJson(`/demo/visitantes/${visitante.id}`, { method: 'DELETE', headers: { 'X-Visitante-Segredo': visitante.segredo } })
      setConsentiu(false); setAberto(false); setApagado(true)
      onApagado()
    } catch (e) {
      const motivo = e instanceof ApiError ? e.message : 'erro inesperado'
      setErro(`Não consegui apagar agora (${motivo}). Tente de novo; de qualquer forma tudo some quando o tempo acabar.`)
    } finally { setApagando(false) }
  }

  const mmss = `${Math.floor(restante / 60000)}:${String(Math.floor(restante / 1000) % 60).padStart(2, '0')}`

  return (
    <section aria-labelledby="teste" className="rounded-xl bg-white p-6 ring-1 ring-concreto-escuro">
      <h2 id="teste" className="font-visor text-2xl font-semibold">Teste com você</h2>
      <p className="mt-1 max-w-prose text-sm text-slate-600">
        Cadastre seu rosto na recepção e passe pela catraca de verdade. Guardamos só um vetor numérico do rosto,
        nunca a foto, e tudo é apagado em 10 minutos.
      </p>

      {apagado && !visitante && (
        <p className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900" role="status">
          Seus dados foram apagados. A câmera continua na catraca: faça um check-in e veja que ela não reconhece
          mais você.
        </p>
      )}

      {!aberto && !visitante && (
        <button onClick={() => setAberto(true)}
          className="mt-4 rounded-lg bg-grafite px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-grafite">
          {apagado ? 'Testar de novo' : 'Quero testar'}
        </button>
      )}

      {aberto && !visitante && (
        <div className="mt-4 space-y-4">
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={consentiu} onChange={(e) => setConsentiu(e.target.checked)} className="mt-1 accent-grafite" />
            <span>Autorizo o uso da minha biometria facial só para este teste, com exclusão automática em 10 minutos.</span>
          </label>
          {consentiu && <Camera rotulo={carregando ? 'Cadastrando…' : 'Cadastrar meu rosto'} onFoto={cadastrar} desabilitado={carregando || ocupado} />}
        </div>
      )}

      {visitante && (
        <div className="mt-4 space-y-3 text-sm">
          <p>
            Pronto, você é <strong>{visitante.nome}</strong> (CPF fictício {visitante.cpf}). A câmera agora está na
            catraca: olhe para ela e toque em <strong>Fazer check-in</strong>.
          </p>
          <p className="text-slate-600">Seus dados somem em <strong className="tabular-nums text-grafite">{mmss}</strong>.</p>
          <button onClick={apagar} disabled={apagando}
            className="font-medium text-red-700 underline underline-offset-4 disabled:cursor-wait disabled:no-underline disabled:opacity-70">
            {apagando ? 'Apagando seus dados…' : 'Apagar meus dados agora'}
          </button>
        </div>
      )}

      {erro && <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">{erro}</p>}
    </section>
  )
}
