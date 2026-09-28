import { useEffect, useRef, useState } from 'react'

export function Camera({ onFoto, rotulo, desabilitado }: { onFoto: (foto: Blob) => void; rotulo: string; desabilitado?: boolean }) {
  const video = useRef<HTMLVideoElement>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'user', width: 640, height: 640 } })
      .then((s) => { stream = s; if (video.current) video.current.srcObject = s })
      .catch(() => setErro('Não consegui acessar a câmera. Verifique a permissão do navegador ou use a galeria acima.'))
    if (!navigator.mediaDevices) setErro('Este navegador não oferece câmera. Use a galeria acima.')
    return () => stream?.getTracks().forEach((t) => t.stop())
  }, [])

  function capturar() {
    const v = video.current
    if (!v || !v.videoWidth) return
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth; canvas.height = v.videoHeight
    canvas.getContext('2d')!.drawImage(v, 0, 0)
    canvas.toBlob((b) => b && onFoto(b), 'image/jpeg', 0.9)
  }

  if (erro) return <p className="rounded-lg bg-amber-500/10 p-3 text-amber-200">{erro}</p>
  return (
    <div className="flex flex-col items-center gap-3">
      <video ref={video} autoPlay playsInline muted className="aspect-square w-64 rounded-2xl bg-black object-cover [transform:scaleX(-1)]" />
      <button onClick={capturar} disabled={desabilitado}
        className="rounded-lg bg-emerald-500 px-4 py-2 font-medium text-slate-950 disabled:opacity-50">{rotulo}</button>
    </div>
  )
}
