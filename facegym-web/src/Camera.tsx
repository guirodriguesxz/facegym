import { useEffect, useRef, useState } from 'react'

/**
 * Vídeo da câmera frontal com um botão que captura um JPEG.
 * `terminal` ocupa a tela inteira da catraca, com o botão por cima do vídeo.
 * `desafio` faz a prova de vida: pede ao servidor para que lado virar, tira uma foto de frente
 * e outra com o rosto virado para esse lado.
 */
export type Desafio = { id: string; direcao: 'ESQUERDA' | 'DIREITA' }

export function Camera({ onFoto, rotulo, desabilitado, terminal, desafio }: {
  onFoto: (foto: Blob, virado?: Blob, desafioId?: string) => void
  rotulo: string
  desabilitado?: boolean
  terminal?: boolean
  desafio?: () => Promise<Desafio>
}) {
  const video = useRef<HTMLVideoElement>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [virando, setVirando] = useState<Desafio['direcao'] | null>(null)
  const [erroDesafio, setErroDesafio] = useState(false)

  useEffect(() => {
    let stream: MediaStream | null = null
    navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'user', width: 640, height: 640 } })
      .then((s) => { stream = s; if (video.current) video.current.srcObject = s })
      .catch(() => setErro('Não consegui acessar a câmera. Libere a permissão no navegador ou use os alunos de demonstração.'))
    if (!navigator.mediaDevices) setErro('Este navegador não oferece câmera. Use os alunos de demonstração.')
    return () => stream?.getTracks().forEach((t) => t.stop())
  }, [])

  function foto(): Promise<Blob | null> {
    const v = video.current
    if (!v || !v.videoWidth) return Promise.resolve(null)
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth; canvas.height = v.videoHeight
    canvas.getContext('2d')!.drawImage(v, 0, 0)
    return new Promise((ok) => canvas.toBlob(ok, 'image/jpeg', 0.9))
  }

  async function capturar() {
    if (!desafio) { const f = await foto(); if (f) onFoto(f); return }
    let d: Desafio
    try { d = await desafio() } catch { setErroDesafio(true); return }
    setErroDesafio(false)
    const frente = await foto()
    if (!frente) return
    setVirando(d.direcao)
    await new Promise((ok) => setTimeout(ok, 1800))
    const virado = await foto()
    setVirando(null)
    if (virado) onFoto(frente, virado, d.id)
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
        {virando && (
          <p role="status" className="absolute inset-x-0 top-6 text-center font-visor text-xl font-semibold text-white drop-shadow">
            {virando === 'ESQUERDA' ? '← Vire o rosto para a sua esquerda' : 'Vire o rosto para a sua direita →'}
          </p>
        )}
        {erroDesafio && !virando && (
          <p role="alert" className="absolute inset-x-0 top-6 text-center font-visor text-lg text-sinal-aviso drop-shadow">
            Não consegui iniciar a verificação. Tente de novo.
          </p>
        )}
        <button onClick={capturar} disabled={desabilitado || virando !== null}
          className="absolute inset-x-6 bottom-5 rounded-full bg-sinal-ok py-2.5 font-visor text-lg font-semibold text-tela focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white disabled:opacity-40">
          {rotulo}
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-start gap-3">
      <video ref={video} autoPlay playsInline muted className="aspect-square w-56 rounded-xl bg-grafite object-cover [transform:scaleX(-1)]" />
      <button onClick={capturar} disabled={desabilitado}
        className="rounded-lg bg-grafite px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-grafite disabled:opacity-50">{rotulo}</button>
    </div>
  )
}
