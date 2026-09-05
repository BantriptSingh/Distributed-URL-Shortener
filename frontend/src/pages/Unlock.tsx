import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api, apiBase } from '../api'

export function Unlock() {
  const { code } = useParams()
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!code) return
    try {
      const res = await api<{ unlockToken: string }>(`/api/v1/urls/${code}/unlock`, {
        method: 'POST',
        body: JSON.stringify({ password }),
      })
      window.location.href = `${apiBase()}/s/${code}?u=${encodeURIComponent(res.unlockToken)}`
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unlock failed')
    }
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-3 rounded-2xl border border-white/10 bg-white/5 p-5">
      <h1 className="text-2xl font-semibold">Unlock /{code}</h1>
      <input
        name="password"
        type="password"
        className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2"
        placeholder="link password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      {error && <p className="text-sm text-rose-300">{error}</p>}
      <button className="rounded-full bg-sky-500 px-5 py-2 font-medium text-slate-950" type="submit">
        Continue
      </button>
    </form>
  )
}
