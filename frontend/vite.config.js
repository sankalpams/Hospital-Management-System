import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Same security headers as deploy/apache/httpd-hms.conf, so `npm run build && npm run preview`
// shows whether the production bundle runs under the production CSP.
const PROD_HEADERS = {
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'same-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
};

// Dev: Vite serves the SPA on :5173 and proxies /api to Spring Boot, so the browser sees a
// single origin (same as production behind Apache) and cookies/CSRF behave identically.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false },
    },
  },
  preview: {
    port: 4173,
    strictPort: true,
    headers: PROD_HEADERS,
    proxy: {
      // HMS_API_TARGET lets a release rehearsal point the preview at an installed backend on another port
      '/api': { target: process.env.HMS_API_TARGET ?? 'http://127.0.0.1:8080', changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    exclude: ['e2e/**', 'node_modules/**'],
    css: false,
  },
});
