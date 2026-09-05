import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, claimFor, getSession } from '../api'

type UrlRow = {
  shortCode: string
  shortUrl: string
  destinationUrl?: string
  isActive: boolean
  clickCount?: number
  createdAt: string
}

type Page = { content: UrlRow[] }

export function Dashboard() {
  const session = getSession()
  const [rows, setRows] = useState<UrlRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [claimMsg, setClaimMsg] = useState<string | null>(null)

  useEffect(() => {
    if (!getSession()) return
    api<Page>('/api/v1/urls')
      .then((p) => setRows(p.content ?? []))
      .catch((e: Error) => setError(e.message))
  }, [])

  if (!session) {
    return <Navigate to="/login" replace />
  }

  async function claim(code: string) {
    const token = claimFor(code)
    if (!token) {
      setClaimMsg(`No guest claim token stored for ${code}`)
      return
    }
    await api(`/api/v1/urls/${code}/claim`, {
      method: 'POST',
      body: JSON.stringify({ claimToken: token }),
    })
    setClaimMsg(`Claimed ${code}`)
    const p = await api<Page>('/api/v1/urls')
    setRows(p.content ?? [])
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      {error && <p className="text-rose-300">{error}</p>}
      {claimMsg && <p className="text-sm text-amber-200">{claimMsg}</p>}
      <div className="overflow-x-auto rounded-2xl border border-white/10">
        <table className="w-full text-left text-sm">
          <thead className="bg-white/5 text-slate-300">
            <tr>
              <th className="px-3 py-2">Code</th>
              <th className="px-3 py-2">Destination</th>
              <th className="px-3 py-2">Clicks</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.shortCode} className="border-t border-white/10">
                <td className="px-3 py-2 font-mono">{r.shortCode}</td>
                <td className="max-w-xs truncate px-3 py-2">{r.destinationUrl}</td>
                <td className="px-3 py-2">{r.clickCount ?? '—'}</td>
                <td className="px-3 py-2">
                  <Link className="text-sky-300" to={`/dashboard/${r.shortCode}`}>
                    Analytics
                  </Link>
                  {claimFor(r.shortCode) && (
                    <button className="ml-3 text-amber-200" type="button" onClick={() => void claim(r.shortCode)}>
                      Claim
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
