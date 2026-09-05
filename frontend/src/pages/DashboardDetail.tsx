import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, apiBase, authHeaders, getSession } from '../api'

type Analytics = {
  totalClicks: number
  realClicks: number
  botClicks: number
  series: { bucket: string; count: number }[]
  countries: { key: string; count: number }[]
  browsers: { key: string; count: number }[]
}

export function DashboardDetail() {
  const { code } = useParams()
  const session = getSession()
  const [data, setData] = useState<Analytics | null>(null)
  const [live, setLive] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!getSession() || !code) return
    api<Analytics>(`/api/v1/urls/${code}/analytics`)
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [code])

  useEffect(() => {
    if (!getSession() || !code) return
    const ctrl = new AbortController()
    void (async () => {
      const res = await fetch(`${apiBase()}/api/v1/urls/${code}/events`, {
        headers: authHeaders(),
        signal: ctrl.signal,
      })
      if (!res.body) return
      const reader = res.body.getReader()
      const dec = new TextDecoder()
      let buf = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buf += dec.decode(value, { stream: true })
        const parts = buf.split('\n\n')
        buf = parts.pop() ?? ''
        for (const part of parts) {
          const line = part.split('\n').find((l) => l.startsWith('data:'))
          if (line) {
            setLive((prev) => [line.slice(5).trim(), ...prev].slice(0, 12))
          }
        }
      }
    })()
    return () => ctrl.abort()
  }, [code])

  if (!session) {
    return <Navigate to="/login" replace />
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">/{code}</h1>
      {error && <p className="text-rose-300">{error}</p>}
      {data && (
        <>
          <div className="grid grid-cols-3 gap-3">
            <Stat label="Total" value={data.totalClicks} />
            <Stat label="Real" value={data.realClicks} />
            <Stat label="Bots" value={data.botClicks} />
          </div>
          <div className="h-56 rounded-2xl border border-white/10 bg-white/5 p-3">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.series.map((s) => ({ ...s, bucket: s.bucket.slice(0, 10) }))}>
                <XAxis dataKey="bucket" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} />
                <Tooltip />
                <Bar dataKey="count" fill="#38bdf8" radius={4} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <List title="Countries" rows={data.countries} />
            <List title="Browsers" rows={data.browsers} />
          </div>
        </>
      )}
      <div>
        <h2 className="mb-2 text-sm uppercase tracking-wide text-slate-400">Live events</h2>
        <ul className="space-y-1 font-mono text-xs text-slate-300">
          {live.map((l, i) => (
            <li key={i}>{l}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="text-2xl font-semibold">{value}</div>
    </div>
  )
}

function List({ title, rows }: { title: string; rows: { key: string; count: number }[] }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
      <h3 className="mb-2 text-sm text-slate-300">{title}</h3>
      <ul className="space-y-1 text-sm">
        {rows.map((r) => (
          <li key={r.key} className="flex justify-between">
            <span>{r.key}</span>
            <span className="text-slate-400">{r.count}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
