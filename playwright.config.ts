import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { defineConfig, devices } from '@playwright/test';

const root = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(root, '.env'), quiet: true });

const testDatabaseUrl = process.env.TEST_DATABASE_URL;
if (!testDatabaseUrl) throw new Error('TEST_DATABASE_URL is not set (copy .env.example to .env)');

// Dedicated ports so a running `npm run dev` (4000/5173) is never disturbed.
const SERVER_PORT = 4100;
const CLIENT_PORT = 5273;
const clientOrigin = `http://localhost:${CLIENT_PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: { baseURL: clientOrigin, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // Prepare hms_test (migrate deploy, truncate, seed small), then serve the API against it.
      command: 'npm run db:e2e-prepare && npx tsx src/server.ts',
      cwd: path.join(root, 'apps/server'),
      url: `http://localhost:${SERVER_PORT}/api/v1/health`,
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        NODE_ENV: 'test',
        PORT: String(SERVER_PORT),
        CLIENT_ORIGIN: clientOrigin,
        DATABASE_URL: testDatabaseUrl,
        TEST_DATABASE_URL: testDatabaseUrl,
        BCRYPT_COST: '4',
        // The suite signs in ~10 times within a minute; production default stays 20.
        LOGIN_RATE_LIMIT: '1000',
      },
    },
    {
      command: 'npx vite',
      cwd: path.join(root, 'apps/client'),
      url: clientOrigin,
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        CLIENT_PORT: String(CLIENT_PORT),
        API_PROXY_TARGET: `http://localhost:${SERVER_PORT}`,
      },
    },
  ],
});
