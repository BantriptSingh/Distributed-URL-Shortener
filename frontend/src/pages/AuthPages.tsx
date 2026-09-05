import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, setSession } from '../api'

export function Login() {
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!email.includes('@') || password.length < 8) {
      setError('Email and password (8+ chars) required.')
      return
    }
    try {
      const s = await api<{ accessToken: string; refreshToken: string; email: string }>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      setSession(s)
      nav('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    }
  }

  return (
    <AuthCard title="Log in" onSubmit={onSubmit} error={error}>
      <input name="email" className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input name="password" type="password" className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <button className="rounded-full bg-sky-500 px-5 py-2 font-medium text-slate-950" type="submit">
        Log in
      </button>
      <p className="text-sm text-slate-400">
        No account? <Link className="text-sky-300" to="/register">Register</Link>
      </p>
    </AuthCard>
  )
}

export function Register() {
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!email.includes('@') || password.length < 8) {
      setError('Email and password (8+ chars) required.')
      return
    }
    try {
      const s = await api<{ accessToken: string; refreshToken: string; email: string }>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      setSession(s)
      nav('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Register failed')
    }
  }

  return (
    <AuthCard title="Register" onSubmit={onSubmit} error={error}>
      <input name="email" className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input name="password" type="password" className="w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="password (8+)" value={password} onChange={(e) => setPassword(e.target.value)} />
      <button className="rounded-full bg-sky-500 px-5 py-2 font-medium text-slate-950" type="submit">
        Create account
      </button>
    </AuthCard>
  )
}

function AuthCard({
  title,
  children,
  onSubmit,
  error,
}: {
  title: string
  children: ReactNode
  onSubmit: (e: FormEvent) => void
  error: string | null
}) {
  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-3 rounded-2xl border border-white/10 bg-white/5 p-5">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {error && <p className="text-sm text-rose-300">{error}</p>}
      {children}
    </form>
  )
}
