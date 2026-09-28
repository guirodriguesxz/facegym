import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiJson, getToken, setToken } from '../api'
import { Login } from './Login'

type Aluno = { id: string; nome: string; cpf: string; bloqueado: boolean; motivoBloqueio: string | null; consentimentoBiometricoEm: string | null }
type Plano = { id: string; nome: string; inicio: string; fim: string; dias: string[]; acessosPorSemana: number | null }
type Acesso = { id: string; dataHora: string; alunoId: string | null; resultado: 'LIBERADO' | 'NEGADO'; motivo: string | null; meio: 'FACIAL' | 'CPF'; score: number | null }

export function Painel() {
  const [logado, setLogado] = useState(!!getToken())
  const [aba, setAba] = useState<'acessos' | 'alunos' | 'planos'>('acessos')
  const [alunos, setAlunos] = useState<Aluno[]>([])
  const [planos, setPlanos] = useState<Plano[]>([])
  const [acessos, setAcessos] = useState<Acesso[]>([])
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    try {
      const [al, pl, ac] = await Promise.all([
        apiJson<Aluno[]>('/alunos'), apiJson<Plano[]>('/planos'), apiJson<Acesso[]>('/acessos?limite=50')])
      setAlunos(al); setPlanos(pl); setAcessos(ac); setErro(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setLogado(false)
      else setErro(e instanceof ApiError ? e.message : 'Erro inesperado')
    }
  }, [])

  useEffect(() => {
    if (!logado) return
    carregar()
    const t = setInterval(carregar, 5000)
    return () => clearInterval(t)
  }, [logado, carregar])

  if (!logado) return <Login onEntrar={() => setLogado(true)} />

  const nomeDe = (id: string | null) => alunos.find((a) => a.id === id)?.nome ?? '—'
  const acao = (fn: () => Promise<unknown>) => fn().then(carregar).catch((e) => setErro(e instanceof ApiError ? e.message : 'Erro'))

  return (
    <main className="mx-auto max-w-5xl px-4 py-8">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Painel <span className="text-emerald-400">FaceGym</span></h1>
        <div className="flex gap-4 text-sm">
          <Link to="/" className="text-slate-400 hover:text-slate-100">← Totem</Link>
          <button onClick={() => { setToken(null); setLogado(false) }} className="text-slate-400 hover:text-rose-300">Sair</button>
        </div>
      </header>
      <nav className="mb-4 flex gap-2">
        {(['acessos', 'alunos', 'planos'] as const).map((a) => (
          <button key={a} onClick={() => setAba(a)}
            className={`rounded-lg px-3 py-1.5 text-sm capitalize ${aba === a ? 'bg-emerald-500 text-slate-950' : 'bg-slate-900 text-slate-300'}`}>{a}</button>
        ))}
      </nav>
      {erro && <p className="mb-4 rounded-lg bg-rose-500/10 p-3 text-rose-300">{erro}</p>}

      {aba === 'acessos' && (
        <div className="overflow-x-auto rounded-xl ring-1 ring-slate-800">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-900 text-slate-400">
              <tr><th className="p-2">Quando</th><th className="p-2">Aluno</th><th className="p-2">Resultado</th><th className="p-2">Meio</th><th className="p-2">Score</th><th className="p-2">Motivo</th></tr>
            </thead>
            <tbody>
              {acessos.map((a) => (
                <tr key={a.id} className="border-t border-slate-800">
                  <td className="p-2 whitespace-nowrap">{new Date(a.dataHora).toLocaleString('pt-BR')}</td>
                  <td className="p-2">{nomeDe(a.alunoId)}</td>
                  <td className={`p-2 ${a.resultado === 'LIBERADO' ? 'text-emerald-400' : 'text-rose-300'}`}>{a.resultado}</td>
                  <td className="p-2">{a.meio}</td>
                  <td className="p-2 tabular-nums">{a.score?.toFixed(2) ?? '—'}</td>
                  <td className="p-2 text-slate-400">{a.motivo ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {aba === 'alunos' && (
        <ul className="space-y-2">
          {alunos.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-900 p-3 ring-1 ring-slate-800">
              <div>
                <p className="font-medium">{a.nome}</p>
                <p className="text-xs text-slate-400">
                  CPF {a.cpf} · biometria {a.consentimentoBiometricoEm ? 'autorizada' : 'sem consentimento'}
                  {a.bloqueado && <span className="text-rose-300"> · bloqueado: {a.motivoBloqueio}</span>}
                </p>
              </div>
              <div className="flex gap-2 text-sm">
                {a.bloqueado
                  ? <button onClick={() => acao(() => apiJson(`/alunos/${a.id}/bloqueio`, { method: 'DELETE' }))} className="rounded bg-slate-800 px-2 py-1">Desbloquear</button>
                  : <button onClick={() => { const m = prompt('Motivo do bloqueio'); if (m) acao(() => apiJson(`/alunos/${a.id}/bloqueio`, { method: 'POST', body: JSON.stringify({ motivo: m }) })) }} className="rounded bg-slate-800 px-2 py-1">Bloquear</button>}
                {a.consentimentoBiometricoEm && (
                  <button onClick={() => { if (confirm(`Apagar a biometria de ${a.nome}?`)) acao(() => apiJson(`/alunos/${a.id}/biometria`, { method: 'DELETE' })) }}
                    className="rounded bg-slate-800 px-2 py-1 text-rose-300">Remover biometria</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {aba === 'planos' && (
        <ul className="grid gap-2 sm:grid-cols-2">
          {planos.map((p) => (
            <li key={p.id} className="rounded-xl bg-slate-900 p-3 ring-1 ring-slate-800">
              <p className="font-medium">{p.nome}</p>
              <p className="text-xs text-slate-400">{p.inicio.slice(0, 5)}–{p.fim.slice(0, 5)} · {p.dias.length} dias/semana · {p.acessosPorSemana ? `${p.acessosPorSemana}x por semana` : 'sem limite'}</p>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
