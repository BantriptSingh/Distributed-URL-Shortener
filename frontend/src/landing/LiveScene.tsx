import { Canvas, useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { Points } from 'three'
import { apiBase } from '../api'

function Cloud({ pulse }: { pulse: number }) {
  const ref = useRef<Points>(null)
  const positions = useMemo(() => {
    const n = 1400
    const arr = new Float32Array(n * 3)
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2
      const r = 0.4 + Math.random() * 2.8
      arr[i * 3] = Math.cos(a) * r
      arr[i * 3 + 1] = (Math.random() - 0.5) * 2.2
      arr[i * 3 + 2] = Math.sin(a) * r
    }
    return arr
  }, [])

  useFrame((_, dt) => {
    const pts = ref.current
    if (!pts) return
    pts.rotation.y += dt * (0.06 + Math.min(pulse, 20) * 0.012)
    pts.rotation.x = Math.sin(Date.now() / 8000) * 0.08
  })

  return (
    <points ref={ref}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
      </bufferGeometry>
      <pointsMaterial color="#7dd3fc" size={0.035} sizeAttenuation transparent opacity={0.9} />
    </points>
  )
}

export default function LiveScene() {
  const [pulse, setPulse] = useState(1)
  useEffect(() => {
    const es = new EventSource(`${apiBase()}/api/v1/analytics/live`)
    const bump = () => setPulse((p) => Math.min(p + 1.2, 48))
    es.addEventListener('click', bump)
    const idle = window.setInterval(() => setPulse((p) => Math.max(0.4, p * 0.92)), 350)
    return () => {
      es.close()
      window.clearInterval(idle)
    }
  }, [])

  return (
    <Canvas camera={{ position: [0, 0.4, 6], fov: 50 }} style={{ height: '100%', width: '100%' }}>
      <color attach="background" args={['#0b1020']} />
      <ambientLight intensity={0.6} />
      <Cloud pulse={pulse} />
    </Canvas>
  )
}
