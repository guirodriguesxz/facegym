import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiForm, apiJson, fotoDeUrl } from './api'
import { Camera } from './Camera'
import { Catraca, type EstadoCatraca } from './Catraca'
import type { RespostaCheckIn } from './resultado'
import { TesteComVoce, type Visitante } from './TesteComVoce'

type AlunoDemo = { slug: string; nome: string; cenario: string }

export function Totem() {
  const [alunos, setAlunos] = useState<AlunoDemo[]>([])
  const [estado, setEstado] = useState<EstadoCatraca>({ fase: 'iniciando' })
  const [foto, setFoto] = useState<string | null>(null)
  const [token, setToken] = useState<string | undefined>()
  const [visitante, setVisitante] = useState<Visitante | null>(null)
  const [cameraNaCatraca, setCameraNaCatraca] = useState(false) // continua depois de apagar, para provar que não reconhece mais

  useEffect(() => {
    fetch('/demo/alunos.json').then((r) => r.json()).then(setAlunos).catch(() => setAlunos([]))
  }, [])

  // o plano free dorme: consulta até a biometria responder e só então libera a catraca
  useEffect(() => {
    let ativo = true
    let espera: ReturnType<typeof setTimeout>
    async function consultar() {
      const s = await apiJson<{ biometria: string }>('/demo/aquecer', { method: 'POST' }).catch(() => null)
      if (!ativo) return
      if (s?.biometria === 'PRONTA') setEstado((e) => (e.fase === 'iniciando' ? { fase: 'pronta' } : e))
      else espera = setTimeout(consultar, 4000)
    }
    consultar()
    return () => { ativo = false; clearTimeout(espera) }
  }, [])

  const ocupado = estado.fase === 'lendo' || estado.fase === 'iniciando'

  async function executar(acao: () => Promise<RespostaCheckIn>) {
    setEstado({ fase: 'lendo' })
    try {
      const r = await acao()
      setToken(r.token)
      setEstado({ fase: 'resposta', resposta: r })
    } catch (e) {
      setEstado({ fase: 'erro', mensagem: e instanceof ApiError ? e.message : 'Erro inesperado' })
    }
  }

  const checkInFoto = (blob: Blob) => executar(() => {
    const form = new FormData(); form.append('foto', blob, 'foto.jpg')
    return apiForm<RespostaCheckIn>('/check-ins', form)
  })

  const checkInCpf = (cpf: string) => executar(() =>
    token
      ? apiJson<RespostaCheckIn>(`/check-ins/${token}/cpf`, { method: 'POST', body: JSON.stringify({ cpf }) })
      : apiJson<RespostaCheckIn>('/check-ins/cpf', { method: 'POST', body: JSON.stringify({ cpf }) }))

  // no celular a catraca fica acima da galeria: rola até ela para mostrar o resultado
  const mostrarCatraca = () => {
    if (window.innerWidth < 1024) document.getElementById('catraca')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function passarAluno(slug: string) {
    mostrarCatraca()
    setVisitante(null)
    setCameraNaCatraca(false)
    setFoto(`/demo/${slug}.jpg`)
    checkInFoto(await fotoDeUrl(`/demo/${slug}.jpg`))
  }

  const tela = visitante || cameraNaCatraca
    ? <Camera terminal rotulo="Fazer check-in" onFoto={checkInFoto} desabilitado={ocupado} />
    : foto
      ? <img src={foto} alt="" className="h-full w-full object-cover" />
      : <div className="h-full w-full bg-[radial-gradient(circle_at_50%_40%,#1b2227,#070b0e_70%)]" />

  return (
    <div className="min-h-screen bg-concreto text-grafite">
      <main className="mx-auto grid max-w-6xl gap-10 px-4 py-8 lg:grid-cols-[380px_1fr] lg:gap-16 lg:py-12">
        <div id="catraca" className="scroll-mt-4 lg:sticky lg:top-8 lg:self-start">
          <Catraca estado={estado} tela={tela} onCpf={checkInCpf} />
        </div>

        <div className="space-y-10">
          <header className="flex flex-wrap items-baseline justify-between gap-3">
            <div>
              <h1 className="font-visor text-4xl font-bold">FaceGym</h1>
              <p className="mt-1 max-w-prose text-slate-600">
                Simulação da catraca de uma academia com reconhecimento facial. A catraca confere plano, horário,
                limite semanal e bloqueio antes de liberar.
              </p>
            </div>
            <Link to="/painel" className="text-sm font-medium text-slate-600 underline-offset-4 hover:text-grafite hover:underline">
              Painel da academia
            </Link>
          </header>

          <section aria-labelledby="galeria">
            <h2 id="galeria" className="font-visor text-2xl font-semibold">Quem vai passar?</h2>
            <p className="mb-4 text-sm text-slate-600">
              Rostos fictícios gerados por IA. A catraca recebe uma foto diferente da cadastrada.
            </p>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
              {alunos.map((a) => (
                <li key={a.slug}>
                  <button disabled={ocupado} onClick={() => passarAluno(a.slug)}
                    className="group w-full overflow-hidden rounded-lg bg-white text-left ring-1 ring-concreto-escuro transition hover:ring-grafite focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-grafite disabled:cursor-not-allowed disabled:opacity-50">
                    <img src={`/demo/${a.slug}.jpg`} alt={`Foto de ${a.nome}`} className="aspect-square w-full object-cover" />
                    <span className="block p-2">
                      <span className="block text-sm font-semibold">{a.nome}</span>
                      <span className="block text-xs text-slate-500">{a.cenario}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <TesteComVoce visitante={visitante} onVisitante={(v) => { setVisitante(v); setCameraNaCatraca(!!v); setFoto(null); if (v) mostrarCatraca() }}
            onApagado={() => { setVisitante(null); setCameraNaCatraca(true); setToken(undefined); setEstado({ fase: 'pronta' }); mostrarCatraca() }} ocupado={ocupado} />
        </div>
      </main>
    </div>
  )
}
