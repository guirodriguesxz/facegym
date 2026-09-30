import { useEffect, useRef, useState } from 'react'
import { SEGUNDOS_DESAFIO, instrucao, type Desafio, type FotosComDesafio, type Lado } from './desafio'

export type ComDesafio = {
  pedir: () => Promise<Desafio>
  onDesafio: (d: Desafio) => void
  onFotos: (f: FotosComDesafio) => void
  onErro: (e: unknown) => void
}

const esperar = (ms: number) => new Promise((ok) => setTimeout(ok, ms))

/**
 * Vídeo da câmera frontal com um botão que captura um JPEG.
 * `terminal` ocupa a tela inteira da catraca, com o botão por cima do vídeo.
 */
export function Camera({ onFoto, comDesafio, rotulo, desabilitado, terminal }: {
  onFoto?: (foto: Blob) => void
  comDesafio?: ComDesafio
  rotulo: string
  desabilitado?: boolean
  terminal?: boolean
}) {
  const video = useRef<HTMLVideoElement>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [contagem, setContagem] = useState<{ lado: Lado; segundos: number } | null>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'user', width: 640, height: 640 } })
      .then((s) => { stream = s; if (video.current) video.current.srcObject = s })
      .catch(() => setErro('Não consegui acessar a câmera. Libere a permissão no navegador ou use os alunos de demonstração.'))
    if (!navigator.mediaDevices) setErro('Este navegador não oferece câmera. Use os alunos de demonstração.')
    return () => stream?.getTracks().forEach((t) => t.stop())
  }, [])

  /** JPEG do quadro atual, sem o espelhamento do preview. */
  function capturar(): Promise<Blob | null> {
    const v = video.current
    if (!v || !v.videoWidth) return Promise.resolve(null)
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth; canvas.height = v.videoHeight
    canvas.getContext('2d')!.drawImage(v, 0, 0)
    return new Promise((ok) => canvas.toBlob(ok, 'image/jpeg', 0.9))
  }

  async function clicar() {
    if (!comDesafio) {
      const foto = await capturar()
      if (foto) onFoto?.(foto)
      return
    }
    try {
      const desafio = await comDesafio.pedir()
      const frente = await capturar()
      if (!frente) return
      comDesafio.onDesafio(desafio)
      for (let s = SEGUNDOS_DESAFIO; s > 0; s--) {
        setContagem({ lado: desafio.lado, segundos: s })
        await esperar(1000)
      }
      const virada = await capturar()
      setContagem(null)
      if (virada) comDesafio.onFotos({ frente, virada, desafio })
    } catch (e) {
      setContagem(null)
      comDesafio.onErro(e)
    }
  }

  if (erro) {
    return terminal
      ? <p className="flex h-full items-center p-6 text-center font-visor text-lg text-sinal-aviso">{erro}</p>
      : <p className="rounded-lg bg-amber-100 p-3 text-sm text-amber-900">{erro}</p>
  }

  if (terminal) {
    return (
      <div className="relative h-full w-full">
        <video ref={video} autoPlay playsInline muted className="h-full w-full object-cover [transform:scaleX(-1)]" />
        {contagem && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/35 font-visor text-white" aria-live="assertive">
            <span className="text-7xl leading-none">{instrucao(contagem.lado).seta}</span>
            <span className="px-6 text-center text-xl font-semibold">{instrucao(contagem.lado).texto}</span>
            <span className="text-4xl tabular-nums">{contagem.segundos}</span>
          </div>
        )}
        <button onClick={clicar} disabled={desabilitado || !!contagem}
          className="absolute inset-x-6 bottom-5 rounded-full bg-sinal-ok py-2.5 font-visor text-lg font-semibold text-tela focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white disabled:opacity-40">
          {rotulo}
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-start gap-3">
      <video ref={video} autoPlay playsInline muted className="aspect-square w-56 rounded-xl bg-grafite object-cover [transform:scaleX(-1)]" />
      <button onClick={clicar} disabled={desabilitado || !!contagem}
        className="rounded-lg bg-grafite px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-grafite disabled:opacity-50">{rotulo}</button>
    </div>
  )
}
