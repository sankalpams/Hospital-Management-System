import { defineConfig } from '@playwright/test';

/**
 * Smoke tests per role against the running dev stack (Vite on 5173, Spring Boot on 8080, dev DB).
 * Uses the Edge already installed on Windows, so no browser download is needed.
 * Run: npm run e2e   (start the backend and `npm run dev` first)
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.js',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.HMS_E2E_URL ?? 'http://localhost:5173',
    channel: 'msedge',
    viewport: { width: 1366, height: 900 },
    trace: 'retain-on-failure',
  },
});
