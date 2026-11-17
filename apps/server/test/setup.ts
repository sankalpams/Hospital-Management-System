import { beforeAll } from 'vitest';
import { resetDb } from './helpers.js';

// Per-file isolation: truncate all tables before each test file runs.
beforeAll(async () => {
  await resetDb();
});
