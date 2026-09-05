import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, apiBase, clearSession, getSession } from '../api'

type KeyRow = { id: number; prefix: string; scopes: string[]; revoked: boolean }
type Created = { key: string; prefix: string }

export function Account() {
  const session = getSession()
  const [keys, setKeys] = useState<KeyRow[]>([])
  const [created, setCreated] = useState<Created | null>(null)
  const [resetEmail, setResetEmail] = useState(session?.email ?? '')
  const [devToken, setDevToken] = useState<string | null>(null)
  const [newPassword, setNewPassword] = useState('')
  const [msg, setMsg] = useState<string | null>(null)

  useEffect(() => {
    if (!getSession()) return
    api<KeyRow[]>('/api/v1/api-keys').then(setKeys).catch(() => setKeys([]))
  }, [])

  async function createKey() {
    const row = await api<Created>('/api/v1/api-keys', {
      method: 'POST',
      body: JSON.stringify({ scopes: ['read', 'write'] }),
    })
    setCreated(row)
    setKeys(await api<KeyRow[]>('/api/v1/api-keys'))
  }

  async function forgot(e: FormEvent) {
    e.preventDefault()
    const res = await fetch(`${apiBase()}/api/v1/auth/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: resetEmail }),
    })
    if (res.status === 204) {
      setDevToken(null)
      setMsg('If that account exists, a reset was issued.')
      return
    }
    const data = (await res.json()) as { devResetToken?: string }
    setDevToken(data.devResetToken ?? null)
    setMsg('DEV reset token returned (local only).')
  }

  async function reset(e: FormEvent) {
    e.preventDefault()
    if (!devToken) return
    await api('/api/v1/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token: devToken, password: newPassword }),
    })
    setMsg('Password updated. Log in again.')
    clearSession()
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-2xl font-semibold">Account</h1>
      {session ? (
        <p className="text-slate-300">
          Signed in as {session.email}{' '}
          <button className="text-sky-300" type="button" onClick={() => { clearSession(); location.href = '/login' }}>
            Log out
          </button>
        </p>
      ) : (
        <p>
          <Link className="text-sky-300" to="/login">Log in</Link> or <Link className="text-sky-300" to="/register">register</Link>.
        </p>
      )}
      {session && (
        <section className="rounded-2xl border border-white/10 bg-white/5 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-medium">API keys</h2>
            <button className="text-sm text-sky-300" type="button" onClick={() => void createKey()}>
              New key
            </button>
          </div>
          {created && <p className="mb-2 break-all text-sm text-amber-200">Shown once: {created.key}</p>}
          <ul className="space-y-1 text-sm">
            {keys.map((k) => (
              <li key={k.id} className="font-mono">
                {k.prefix} {k.revoked ? '(revoked)' : ''}
              </li>
            ))}
          </ul>
        </section>
      )}
      <form onSubmit={forgot} className="space-y-2 rounded-2xl border border-white/10 bg-white/5 p-4">
        <h2 className="font-medium">Forgot password</h2>
        <input className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" value={resetEmail} onChange={(e) => setResetEmail(e.target.value)} />
        <button className="text-sm text-sky-300" type="submit">Send reset</button>
        {msg && <p className="text-sm text-slate-300">{msg}</p>}
        {devToken && <p className="break-all text-xs text-amber-200">DEV ONLY token: {devToken}</p>}
      </form>
      {devToken && (
        <form onSubmit={reset} className="space-y-2">
          <input className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" type="password" placeholder="new password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          <button className="text-sm text-sky-300" type="submit">Reset password</button>
        </form>
      )}
    </div>
  )
}
