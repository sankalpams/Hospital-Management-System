/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: Number(process.env.CLIENT_PORT ?? 5173),
    strictPort: true,
    proxy: { '/api': { target: process.env.API_PROXY_TARGET ?? 'http://localhost:4000', changeOrigin: true } },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
