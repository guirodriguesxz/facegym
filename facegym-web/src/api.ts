export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api/v1'
const TOKEN_KEY = 'facegym.token'

export class ApiError extends Error {
  status: number
  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.status = status
  }
}

export function getToken(): string | null {
  try { return sessionStorage.getItem(TOKEN_KEY) } catch { return null }
}
export function setToken(t: string | null) {
  try { if (t) sessionStorage.setItem(TOKEN_KEY, t); else sessionStorage.removeItem(TOKEN_KEY) } catch { /* sem storage */ }
}

/**
 * Token do totem físico (cadastrado no painel). Fica só neste aparelho; sem ele a catraca
 * alcança apenas os alunos fictícios da demo e os visitantes.
 * Configure abrindo /#totem=<token> uma vez: o fragmento (#) não vai para servidores nem logs.
 */
const TOTEM_KEY = 'facegym.totem'
export function getTotem(): string | null {
  try { return localStorage.getItem(TOTEM_KEY) } catch { return null }
}
export function setTotem(t: string | null) {
  try { if (t) localStorage.setItem(TOTEM_KEY, t); else localStorage.removeItem(TOTEM_KEY) } catch { /* sem storage */ }
}
export function configurarTotemPelaUrl() {
  const m = window.location.hash.match(/^#totem=([0-9a-f]{64})$/)
  if (!m) return
  setTotem(m[1])
  history.replaceState(null, '', window.location.pathname + window.location.search) // tira o token da barra
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()
  const totem = getTotem()
  if (totem && path.startsWith('/check-ins')) headers.set('X-Totem-Token', totem)
  if (token && path.startsWith('/') && !path.startsWith('/check-ins') && !path.startsWith('/demo')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  let res: Response
  try {
    res = await fetch(`${API_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Não foi possível falar com o servidor. Ele pode estar acordando; tente de novo em alguns segundos.')
  }
  if (res.status === 401 && token) setToken(null)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.mensagem ?? `Erro ${res.status}`)
  }
  if (res.status === 204 || res.status === 202) return undefined as T
  return (await res.json()) as T
}

export const apiJson = <T>(path: string, init: RequestInit = {}) =>
  request<T>(path, { ...init, headers: { 'Content-Type': 'application/json', ...init.headers } })

export const apiForm = <T>(path: string, form: FormData, method = 'POST') =>
  request<T>(path, { method, body: form })

export async function fotoDeUrl(url: string): Promise<Blob> {
  return (await fetch(url)).blob()
}
