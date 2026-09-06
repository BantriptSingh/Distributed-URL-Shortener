import { expect, test } from '@playwright/test'

const API = process.env.E2E_API_URL ?? 'http://localhost:8080'

test('shorten on API, redirect, then frontend loads', async ({ request, page }) => {
  const code = 'sm' + Date.now().toString(36).slice(-5)
  const created = await request.post(`${API}/api/v1/urls`, {
    data: { destinationUrl: 'https://example.com/e2e', customCode: code },
  })
  expect(created.ok()).toBeTruthy()
  const body = (await created.json()) as { shortCode: string; shortUrl: string }
  const redirect = await request.get(`${API}/s/${body.shortCode}`, { maxRedirects: 0 })
  expect(redirect.status()).toBe(302)
  expect(redirect.headers()['location']).toBe('https://example.com/e2e')
  await page.goto('/shorten')
  await expect(page.getByRole('heading', { name: 'Shorten' })).toBeVisible()
})
