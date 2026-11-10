import { defineConfig } from '@playwright/test'

// E2E runs against the full app (Spring on :8080 serves /app/) in headless Chromium.
export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['line'], ['html', { open: 'never' }]] : 'line',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080', trace: 'retain-on-failure' },
})
