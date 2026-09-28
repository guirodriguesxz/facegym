import { useState, type FormEvent } from 'react'
import { ApiError, apiJson, setToken } from '../api'

export function Login({ onEntrar }: { onEntrar: () => void }) {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)

  async function entrar(e: FormEvent) {
    e.preventDefault(); setErro(null)
    try {
      const { token } = await apiJson<{ token: string }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, senha }) })
      setToken(token); onEntrar()
    } catch (e) { setErro(e instanceof ApiError ? e.message : 'Erro inesperado') }
  }

  return (
    <form onSubmit={entrar} className="mx-auto mt-16 max-w-sm space-y-3 rounded-2xl bg-slate-900 p-6 ring-1 ring-slate-800">
      <h1 className="text-xl font-semibold">Painel da academia</h1>
      <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="E-mail" required autoComplete="username"
        className="w-full rounded-lg bg-slate-950 px-3 py-2 ring-1 ring-slate-700" />
      <input value={senha} onChange={(e) => setSenha(e.target.value)} type="password" placeholder="Senha" required autoComplete="current-password"
        className="w-full rounded-lg bg-slate-950 px-3 py-2 ring-1 ring-slate-700" />
      {erro && <p className="text-sm text-rose-300">{erro}</p>}
      <button className="w-full rounded-lg bg-emerald-500 py-2 font-medium text-slate-950">Entrar</button>
    </form>
  )
}
