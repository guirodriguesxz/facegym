import { useEffect, useState, type ReactNode } from 'react'
import { descrever, formatarCpf, type RespostaCheckIn } from './resultado'

export type EstadoCatraca =
  | { fase: 'iniciando' }
  | { fase: 'pronta' }
  | { fase: 'lendo' }
  | { fase: 'resposta'; resposta: RespostaCheckIn }
  | { fase: 'erro'; mensagem: string }

const LUZ = { ok: 'bg-sinal-ok shadow-[0_0_18px_4px] shadow-sinal-ok/70', erro: 'bg-sinal-erro shadow-[0_0_18px_4px] shadow-sinal-erro/70', aviso: 'bg-sinal-aviso shadow-[0_0_18px_4px] shadow-sinal-aviso/60', apagada: 'bg-grafite-claro' }

/** Terminal de reconhecimento facial preso na catraca: tela, barra de luz e braço. */
export function Catraca({ estado, tela, onCpf }: {
  estado: EstadoCatraca
  tela: ReactNode
  onCpf: (cpf: string) => void
}) {
  const d = estado.fase === 'resposta' ? descrever(estado.resposta) : null
  const luz = d ? d.tom : estado.fase === 'erro' ? 'erro' : estado.fase === 'iniciando' ? 'aviso' : 'apagada'
  const liberado = estado.fase === 'resposta' && estado.resposta.status === 'LIBERADO'

  return (
    <figure className="mx-auto w-full max-w-[340px]" aria-label="Terminal da catraca">
      <div className="rounded-[28px] bg-grafite p-4 pb-5 shadow-[0_30px_60px_-20px_rgba(20,22,24,.55)] ring-1 ring-black/40">
        <div className="mb-3 flex items-center justify-center gap-2" aria-hidden>
          <span className="h-1.5 w-1.5 rounded-full bg-red-900" />
          <span className="h-3 w-3 rounded-full bg-black ring-2 ring-grafite-claro" />
          <span className="h-1.5 w-1.5 rounded-full bg-red-900" />
        </div>

        <div className="overflow-hidden rounded-xl bg-tela text-slate-100 ring-1 ring-black">
          <BarraSuperior />
          <div className="relative aspect-[4/5] overflow-hidden bg-black">
            {tela}
            {estado.fase !== 'iniciando' && <GuiaDoRosto />}
            {estado.fase === 'lendo' && <span className="linha-leitura pointer-events-none absolute inset-x-8 h-0.5 bg-sinal-ok/90 shadow-[0_0_12px_2px] shadow-sinal-ok" />}
          </div>
          <Visor estado={estado} onCpf={onCpf} />
        </div>

        <div className={`mx-auto mt-4 h-1.5 w-2/3 rounded-full transition-colors duration-300 ${LUZ[luz]}`} role="presentation" />
      </div>

      <Braco liberado={liberado} />
    </figure>
  )
}

function BarraSuperior() {
  const [agora, setAgora] = useState(() => new Date())
  useEffect(() => {
    const t = setInterval(() => setAgora(new Date()), 15_000)
    return () => clearInterval(t)
  }, [])
  return (
    <div className="flex items-center justify-between px-3 py-1.5 font-visor text-sm text-slate-400">
      <span className="font-semibold tracking-wide text-slate-200">FaceGym</span>
      <time>{agora.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</time>
    </div>
  )
}

function GuiaDoRosto() {
  return (
    <svg viewBox="0 0 100 125" className="pointer-events-none absolute inset-0 h-full w-full" aria-hidden>
      <ellipse cx="50" cy="56" rx="30" ry="39" fill="none" stroke="white" strokeOpacity=".55" strokeWidth=".8" strokeDasharray="3 2.5" />
    </svg>
  )
}

function Visor({ estado, onCpf }: { estado: EstadoCatraca; onCpf: (cpf: string) => void }) {
  if (estado.fase === 'iniciando') {
    return <Mensagem titulo="Iniciando o terminal" detalhe="O servidor gratuito estava dormindo. Leva até 1 minuto." cor="text-sinal-aviso" />
  }
  if (estado.fase === 'pronta') return <Mensagem titulo="Olhe para a câmera" detalhe="Escolha quem vai passar pela catraca." />
  if (estado.fase === 'lendo') return <Mensagem titulo="Identificando…" detalhe="Mantenha o rosto dentro da marcação." />
  if (estado.fase === 'erro') return <Mensagem titulo="Sem conexão" detalhe={estado.mensagem} cor="text-sinal-erro" />

  const d = descrever(estado.resposta)
  const cor = d.tom === 'ok' ? 'text-sinal-ok' : d.tom === 'erro' ? 'text-sinal-erro' : 'text-sinal-aviso'
  return (
    <div>
      <Mensagem titulo={d.titulo} detalhe={d.tom === 'ok' ? 'Pode passar.' : d.detalhe} cor={cor} />
      {d.pedeCpf && <Teclado key={estado.resposta.token ?? estado.resposta.status} onCpf={onCpf} />}
    </div>
  )
}

function Mensagem({ titulo, detalhe, cor = 'text-slate-100' }: { titulo: string; detalhe?: string; cor?: string }) {
  return (
    <div className="px-4 py-3 text-center" role="status" aria-live="polite">
      <p className={`font-visor text-2xl leading-tight font-semibold ${cor}`}>{titulo}</p>
      {detalhe && <p className="mt-0.5 text-sm text-slate-400">{detalhe}</p>}
    </div>
  )
}

function Teclado({ onCpf }: { onCpf: (cpf: string) => void }) {
  const [digitos, setDigitos] = useState('')
  const teclas = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'apagar', '0', 'ok']
  function tocar(t: string) {
    if (t === 'apagar') setDigitos((d) => d.slice(0, -1))
    else if (t === 'ok') { if (digitos.length === 11) onCpf(digitos) }
    else setDigitos((d) => (d.length < 11 ? d + t : d))
  }
  return (
    <div className="px-4 pb-4">
      <output className="mb-2 block rounded-md bg-black/60 py-1.5 text-center font-visor text-xl tracking-wider tabular-nums text-slate-100" aria-label="CPF digitado">
        {formatarCpf(digitos) || <span className="text-slate-600">000.000.000-00</span>}
      </output>
      <div className="grid grid-cols-3 gap-1.5">
        {teclas.map((t) => (
          <button key={t} type="button" onClick={() => tocar(t)} disabled={t === 'ok' && digitos.length !== 11}
            aria-label={t === 'apagar' ? 'Apagar dígito' : t === 'ok' ? 'Confirmar CPF' : undefined}
            className={`rounded-md py-2 font-visor text-lg font-semibold focus-visible:outline-2 focus-visible:outline-white disabled:opacity-30 ${
              t === 'ok' ? 'bg-sinal-ok text-tela' : 'bg-grafite-claro text-slate-100 active:bg-slate-600'}`}>
            {t === 'apagar' ? '⌫' : t === 'ok' ? 'OK' : t}
          </button>
        ))}
      </div>
    </div>
  )
}

/** Vista de cima do pedestal com o braço que gira ao liberar. */
function Braco({ liberado }: { liberado: boolean }) {
  return (
    <svg viewBox="0 0 340 70" className="mt-3 w-full" aria-hidden>
      <rect x="120" y="10" width="100" height="50" rx="10" fill="var(--color-grafite)" />
      <rect x="132" y="22" width="76" height="26" rx="6" fill="var(--color-grafite-claro)" />
      <g transform="translate(214 35)">
        <g className="braco" data-liberado={liberado}>
          <rect x="-6" y="-5" width="120" height="10" rx="5" fill="#9aa0a6" />
          <circle cx="0" cy="0" r="9" fill="#6b7177" />
        </g>
      </g>
    </svg>
  )
}
