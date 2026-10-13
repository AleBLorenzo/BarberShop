import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  preview: {
    // Permite los hostnames de los quick tunnels (*.trycloudflare.com) usados
    // para que el cliente vea la demo; Vite bloquea Host externos por defecto.
    allowedHosts: ['.trycloudflare.com'],
  },
})