import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { Shorten } from '../pages/Shorten'
import { Login } from '../pages/AuthPages'
import { authHeaders, setSession } from '../api'

describe('Shorten form', () => {
  it('disables create when the destination is not http(s)', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(
      <MemoryRouter>
        <Shorten />
      </MemoryRouter>,
    )
    await userEvent.type(screen.getByPlaceholderText('https://example.com/very/long'), 'javascript:alert(1)')
    expect(screen.getByRole('button', { name: /create short link/i })).toBeDisabled()
    expect(fetchMock).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})

describe('auth headers', () => {
  it('attaches a bearer token after session is stored', () => {
    setSession({ accessToken: 'tok-abc', refreshToken: 'r', email: 'a@b.c' })
    expect(authHeaders()).toEqual({ Authorization: 'Bearer tok-abc' })
    localStorage.clear()
  })
})

describe('login validation', () => {
  it('requires email and a long enough password', async () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    )
    await userEvent.click(screen.getByRole('button', { name: /log in/i }))
    expect(screen.getByText(/email and password/i)).toBeInTheDocument()
  })
})
