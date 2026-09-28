import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiForm, apiJson, fotoDeUrl } from './api'
import { ResultadoCard } from './ResultadoCard'
import type { RespostaCheckIn } from './resultado'
import { TesteComVoce } from './TesteComVoce'

type AlunoDemo = { slug: string; nome: string; cenario: string }

export function Totem() {
  const [alunos, setAlunos] = useState<AlunoDemo[]>([])
  const [resposta, setResposta] = useState<RespostaCheckIn | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    fetch('/demo/alunos.json').then((r) => r.json()).then(setAlunos).catch(() => setAlunos([]))
    apiJson('/demo/aquecer', { method: 'POST' }).catch(() => {}) // acorda a biometria no Render free
  }, [])

  async function executar(acao: () => Promise<RespostaCheckIn>) {
    setOcupado(true); setErro(null)
    try { setResposta(await acao()) }
    catch (e) { setErro(e instanceof ApiError ? e.message : 'Erro inesperado') }
    finally { setOcupado(false) }
  }

  const checkInFoto = (foto: Blob) => executar(() => {
    const form = new FormData(); form.append('foto', foto, 'foto.jpg')
    return apiForm<RespostaCheckIn>('/check-ins', form)
  })

  const checkInCpf = (cpf: string) => executar(() =>
    resposta?.status === 'CONFIRMAR_CPF' && resposta.token
      ? apiJson<RespostaCheckIn>(`/check-ins/${resposta.token}/cpf`, { method: 'POST', body: JSON.stringify({ cpf }) })
      : apiJson<RespostaCheckIn>('/check-ins/cpf', { method: 'POST', body: JSON.stringify({ cpf }) }))

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 flex items-center justify-between">
        <h1 className="text-2xl font-bold">FaceGym <span className="text-emerald-400">· totem</span></h1>
        <Link to="/painel" className="text-sm text-slate-400 hover:text-slate-100">Painel da academia →</Link>
      </header>

      <section aria-labelledby="galeria">
        <h2 id="galeria" className="mb-1 text-lg font-semibold">Alunos de demonstração</h2>
        <p className="mb-4 text-sm text-slate-400">Rostos fictícios gerados por IA. Clique em um para fazer o check-in com uma foto diferente da cadastrada.</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {alunos.map((a) => (
            <button key={a.slug} disabled={ocupado} onClick={async () => checkInFoto(await fotoDeUrl(`/demo/${a.slug}.jpg`))}
              className="overflow-hidden rounded-xl bg-slate-900 text-left ring-1 ring-slate-800 transition hover:ring-emerald-500 disabled:opacity-50">
              <img src={`/demo/${a.slug}.jpg`} alt={`Foto de ${a.nome}`} className="aspect-square w-full object-cover" />
              <div className="p-2">
                <p className="text-sm font-medium">{a.nome}</p>
                <p className="text-xs text-slate-400">{a.cenario}</p>
              </div>
            </button>
          ))}
        </div>
      </section>

      <section className="mt-6 min-h-24">
        {ocupado && <p className="text-slate-400">Reconhecendo… (na primeira vez o servidor pode levar até 1 minuto para acordar)</p>}
        {erro && <p className="rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}
        {resposta && !ocupado && <ResultadoCard resposta={resposta} onCpf={checkInCpf} ocupado={ocupado} />}
      </section>

      <TesteComVoce onCheckIn={checkInFoto} ocupado={ocupado} />
    </main>
  )
}
