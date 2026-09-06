import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Nav } from './Nav'
import { Account } from './pages/Account'
import { Login, Register } from './pages/AuthPages'
import { Shorten } from './pages/Shorten'
import { Unlock } from './pages/Unlock'

const Landing = lazy(() => import('./pages/Landing').then((m) => ({ default: m.Landing })))
const Dashboard = lazy(() => import('./pages/Dashboard').then((m) => ({ default: m.Dashboard })))
const DashboardDetail = lazy(() =>
  import('./pages/DashboardDetail').then((m) => ({ default: m.DashboardDetail })),
)

export default function App() {
  return (
    <BrowserRouter>
      <Nav />
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Suspense fallback={null}>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/shorten" element={<Shorten />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/dashboard/:code" element={<DashboardDetail />} />
            <Route path="/account" element={<Account />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/unlock/:code" element={<Unlock />} />
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
  )
}
