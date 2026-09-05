import { lazy, Suspense, useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { motion } from 'framer-motion'

const LiveScene = lazy(() => import('../landing/LiveScene'))

function canUseWebGl(): boolean {
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'))
  } catch {
    return false
  }
}

function CssField() {
  return (
    <div className="pointer-events-none absolute inset-0 opacity-50" aria-hidden>
      {Array.from({ length: 48 }).map((_, i) => (
        <span
          key={i}
          className="absolute h-1 w-1 rounded-full bg-sky-300"
          style={{
            left: `${(i * 17) % 100}%`,
            top: `${(i * 29) % 80}%`,
            animation: `pulse ${2 + (i % 5)}s ease-in-out infinite`,
          }}
        />
      ))}
    </div>
  )
}

export function Landing() {
  const [use3d, setUse3d] = useState(false)

  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    setUse3d(!reduce && canUseWebGl())
  }, [])

  return (
    <div className="relative min-h-[28rem] overflow-hidden">
      {use3d ? (
        <div className="absolute inset-0">
          <Suspense fallback={<CssField />}>
            <LiveScene />
          </Suspense>
        </div>
      ) : (
        <CssField />
      )}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        className="relative z-10 mx-auto max-w-2xl pt-16 text-center"
      >
        <p className="text-sm uppercase tracking-[0.2em] text-sky-300">distributed short links</p>
        <h1 className="mt-4 text-4xl font-semibold leading-tight sm:text-5xl">
          Short URLs with live click pulse.
        </h1>
        <p className="mt-4 text-slate-300">
          Create a link in seconds. Click intensity on this page is driven by the public live stream.
        </p>
        <NavLink
          to="/shorten"
          className="mt-8 inline-block rounded-full bg-sky-500 px-6 py-3 font-medium text-slate-950"
        >
          Shorten a URL
        </NavLink>
      </motion.div>
    </div>
  )
}
