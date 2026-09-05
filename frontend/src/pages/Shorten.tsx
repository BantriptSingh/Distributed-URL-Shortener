import { useMemo, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { api, storeClaim } from '../api'

type CreateResponse = {
  shortCode: string
  shortUrl: string
  destinationUrl: string
  claimToken?: string | null
}

export function Shorten() {
  const [destinationUrl, setDestinationUrl] = useState('')
  const [advanced, setAdvanced] = useState(false)
  const [customCode, setCustomCode] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [tags, setTags] = useState('')
  const [maxClicks, setMaxClicks] = useState('')
  const [publicClickCount, setPublicClickCount] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<CreateResponse | null>(null)
  const [qr, setQr] = useState<string | null>(null)

  const canSubmit = useMemo(() => {
    try {
      const u = new URL(destinationUrl)
      return u.protocol === 'http:' || u.protocol === 'https:'
    } catch {
      return false
    }
  }, [destinationUrl])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!canSubmit) {
      setError('Enter a valid http(s) URL.')
      return
    }
    const body: Record<string, unknown> = {
      destinationUrl,
      publicClickCount,
    }
    if (customCode.trim()) body.customCode = customCode.trim()
    if (expiresAt) body.expiresAt = new Date(expiresAt).toISOString()
    if (tags.trim()) body.tags = tags.split(',').map((t) => t.trim()).filter(Boolean)
    if (maxClicks) body.maxClicks = Number(maxClicks)
    if (password) body.password = password
    try {
      const created = await api<CreateResponse>('/api/v1/urls', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      setResult(created)
      if (created.claimToken) {
        storeClaim(created.shortCode, created.claimToken)
      }
      setQr(await QRCode.toDataURL(created.shortUrl, { margin: 1, width: 180 }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed')
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <h1 className="text-2xl font-semibold">Shorten</h1>
      <form onSubmit={onSubmit} className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5">
        <label className="block text-sm">
          Destination URL
          <input
            className="mt-1 w-full rounded-lg border border-white/10 bg-slate-950 px-3 py-2"
            value={destinationUrl}
            onChange={(e) => setDestinationUrl(e.target.value)}
            placeholder="https://example.com/very/long"
            name="destinationUrl"
          />
        </label>
        <button type="button" className="text-sm text-sky-300" onClick={() => setAdvanced((v) => !v)}>
          {advanced ? 'Hide advanced' : 'Advanced'}
        </button>
        {advanced && (
          <div className="grid gap-3 sm:grid-cols-2">
            <input className="rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="custom alias" value={customCode} onChange={(e) => setCustomCode(e.target.value)} />
            <input className="rounded-lg border border-white/10 bg-slate-950 px-3 py-2" type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
            <input className="rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="tags, comma separated" value={tags} onChange={(e) => setTags(e.target.value)} />
            <input className="rounded-lg border border-white/10 bg-slate-950 px-3 py-2" placeholder="max clicks" value={maxClicks} onChange={(e) => setMaxClicks(e.target.value)} />
            <input className="rounded-lg border border-white/10 bg-slate-950 px-3 py-2 sm:col-span-2" type="password" placeholder="optional password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input type="checkbox" checked={publicClickCount} onChange={(e) => setPublicClickCount(e.target.checked)} />
              Public click count
            </label>
          </div>
        )}
        {error && <p className="text-sm text-rose-300">{error}</p>}
        <button
          disabled={!canSubmit}
          className="rounded-full bg-sky-500 px-5 py-2 font-medium text-slate-950 disabled:opacity-40"
          type="submit"
        >
          Create short link
        </button>
      </form>
      {result && (
        <div className="rounded-2xl border border-white/10 bg-white/5 p-5">
          <p className="font-mono text-sky-200">{result.shortUrl}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              className="rounded-full border border-white/20 px-3 py-1 text-sm"
              onClick={() => navigator.clipboard.writeText(result.shortUrl)}
            >
              Copy
            </button>
          </div>
          {qr && <img alt="QR code" className="mt-4 rounded-lg bg-white p-2" src={qr} width={180} height={180} />}
          {result.claimToken && (
            <p className="mt-3 text-sm text-amber-200">
              Guest claim token saved in this browser. Register and open the dashboard to claim <code>{result.shortCode}</code>.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
