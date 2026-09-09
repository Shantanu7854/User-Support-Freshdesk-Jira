import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // Dev only. In production the built assets are served BY Spring Boot from
    // the same origin, so there is no CORS configuration anywhere.
    proxy: { '/api': 'http://localhost:8080' }
  },
  build: { outDir: 'dist' }
})
