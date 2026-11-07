import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev, proxy the API and WebSocket through Vite so the browser talks to a single origin,
// exactly like production (where nginx does the same). No CORS in either environment.
const backend = process.env.VITE_BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true, changeOrigin: true },
    },
  },
})
