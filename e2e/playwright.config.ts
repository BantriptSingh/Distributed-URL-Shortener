import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  use: {
    baseURL: process.env.E2E_WEB_URL ?? 'http://localhost:8081',
  },
  retries: 0,
})
