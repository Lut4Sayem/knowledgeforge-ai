/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const spring = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  base: '/app/',
  build: { outDir: '../target/classes/static/app', emptyOutDir: true },
  server: {
    port: 5173,
    proxy: { '/api': spring, '/logout': spring, '/internal': spring },
  },
  test: { environment: 'jsdom' },
})
