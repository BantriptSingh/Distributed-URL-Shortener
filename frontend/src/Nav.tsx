import { NavLink } from 'react-router-dom'
import { docsUrl, getSession } from './api'

export function Nav() {
  const session = getSession()
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-full px-3 py-1.5 text-sm ${isActive ? 'bg-sky-500/20 text-sky-200' : 'text-slate-300 hover:text-white'}`

  return (
    <header className="sticky top-0 z-20 border-b border-white/10 bg-[#0b1020]/80 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <NavLink to="/" className="font-semibold tracking-tight">
          Shorty
        </NavLink>
        <nav className="flex flex-wrap items-center gap-1">
          <NavLink to="/shorten" className={link}>
            Shorten
          </NavLink>
          <NavLink to="/dashboard" className={link}>
            Dashboard
          </NavLink>
          <a className={link({ isActive: false })} href={docsUrl()} target="_blank" rel="noreferrer">
            Docs
          </a>
          <NavLink to="/account" className={link}>
            Account
          </NavLink>
        </nav>
        <span className="hidden text-xs text-slate-400 sm:inline">{session?.email ?? 'guest'}</span>
      </div>
    </header>
  )
}
