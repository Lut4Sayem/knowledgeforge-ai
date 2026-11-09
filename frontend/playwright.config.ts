import { defineConfig } from '@playwright/test'

// E2E runs against the full app (Spring on :8080 serves /app/). Specs arrive with the features.
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080' },
})
