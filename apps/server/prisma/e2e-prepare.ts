/**
 * Prepares the TEST database (hms_test) for the Playwright e2e run:
 * migrate deploy -> truncate every table -> seed with SEED_SCALE=small.
 * Never touches the dev database and never uses `prisma migrate reset`.
 * Shares hms_test with `npm test`, so the two must not run at the same time.
 */
import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { PrismaClient } from '@prisma/client';

const serverDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
dotenv.config({ path: path.resolve(serverDir, '../../.env'), quiet: true });

const url = process.env.TEST_DATABASE_URL;
if (!url) throw new Error('TEST_DATABASE_URL is not set (see .env.example)');

console.log('[e2e-prepare] prisma migrate deploy');
execSync('npx prisma migrate deploy', {
  cwd: serverDir,
  env: { ...process.env, DATABASE_URL: url },
  stdio: 'inherit',
});

const prisma = new PrismaClient({ datasourceUrl: url });
try {
  const rows = await prisma.$queryRaw<{ tablename: string }[]>`
    SELECT tablename FROM pg_tables
    WHERE schemaname = 'public' AND tablename <> '_prisma_migrations'`;
  if (rows.length > 0) {
    const list = rows.map((r) => `"public"."${r.tablename}"`).join(', ');
    await prisma.$executeRawUnsafe(`TRUNCATE TABLE ${list} RESTART IDENTITY CASCADE`);
  }
  console.log('[e2e-prepare] tables truncated; seeding (SEED_SCALE=small)');
  // Imported late so the seed modules never see a different DATABASE_URL.
  const { runSeed } = await import('./seed/index.js');
  await runSeed({ prisma, scale: 'small' });
} finally {
  await prisma.$disconnect();
}
