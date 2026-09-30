/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // import '@/shared/api/client' instead of '../../../shared/api/client' (same in tsconfig.json).
  resolve: {
    alias: { '@': '/src' },
  },
  server: {
    // Listen on the LAN too, so a phone can open the guest page during the demo.
    host: true,
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  // P3-04: component tests render into a simulated browser.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
