import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const here = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(here, '../../../.env'), quiet: true });

export const config = {
  nodeEnv: process.env.NODE_ENV ?? 'development',
  port: Number(process.env.PORT ?? 4000),
  clientOrigin: process.env.CLIENT_ORIGIN ?? 'http://localhost:5173',
  /** Max login attempts per minute per IP (ruling R2). Raised only by the e2e run. */
  loginRateLimit: Number(process.env.LOGIN_RATE_LIMIT ?? 20),
  databaseUrl: process.env.DATABASE_URL,
};
