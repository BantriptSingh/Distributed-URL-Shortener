const API = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080'

export function apiBase(): string {
  return API.replace(/\/$/, '')
}

export function docsUrl(): string {
  return `${apiBase()}/swagger-ui.html`
}

export type Session = {
  accessToken: string
  refreshToken: string
  email: string
}

const ACCESS = 'accessToken'
const REFRESH = 'refreshToken'
const EMAIL = 'email'
const CLAIMS = 'guestClaims'

export function getSession(): Session | null {
  const accessToken = localStorage.getItem(ACCESS)
  const refreshToken = localStorage.getItem(REFRESH)
  const email = localStorage.getItem(EMAIL)
  if (!accessToken || !refreshToken || !email) {
    return null
  }
  return { accessToken, refreshToken, email }
}

export function setSession(s: Session): void {
  localStorage.setItem(ACCESS, s.accessToken)
  localStorage.setItem(REFRESH, s.refreshToken)
  localStorage.setItem(EMAIL, s.email)
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS)
  localStorage.removeItem(REFRESH)
  localStorage.removeItem(EMAIL)
}

export function authHeaders(): HeadersInit {
  const s = getSession()
  return s ? { Authorization: `Bearer ${s.accessToken}` } : {}
}

export function storeClaim(code: string, token: string): void {
  const raw = localStorage.getItem(CLAIMS)
  const map = raw ? (JSON.parse(raw) as Record<string, string>) : {}
  map[code] = token
  localStorage.setItem(CLAIMS, JSON.stringify(map))
}

export function claimFor(code: string): string | null {
  const raw = localStorage.getItem(CLAIMS)
  if (!raw) {
    return null
  }
  return (JSON.parse(raw) as Record<string, string>)[code] ?? null
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const auth = authHeaders()
  if ('Authorization' in auth && !headers.has('Authorization')) {
    headers.set('Authorization', String(auth.Authorization))
  }
  const res = await fetch(`${apiBase()}${path}`, { ...init, headers })
  if (res.status === 204) {
    return undefined as T
  }
  const text = await res.text()
  const data = text ? (JSON.parse(text) as T & { code?: string; message?: string }) : ({} as T)
  if (!res.ok) {
    const err = data as { code?: string; message?: string }
    throw new ApiError(res.status, err.code ?? 'error', err.message ?? res.statusText)
  }
  return data
}
