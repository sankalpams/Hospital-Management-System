import { beforeEach, describe, expect, it } from 'vitest';
import express from 'express';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import { errorHandler } from './errorHandler.js';
import { requireAuth } from './requireAuth.js';
import { requirePermission } from './requirePermission.js';
import { createUser, resetDb, testApp } from '../../test/helpers.js';

function guardedApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.get('/guarded', requireAuth(), requirePermission('user:manage'), (_req, res) => {
    res.json({ ok: true });
  });
  app.get('/any', requireAuth(), requirePermission('user:manage', 'emr:write'), (_req, res) => {
    res.json({ ok: true });
  });
  app.use(errorHandler);
  return app;
}

async function sessionAgent(username: string, role: 'ADMIN' | 'DOCTOR') {
  await createUser({ role, username });
  const login = await request(testApp())
    .post('/api/v1/auth/login')
    .send({ username, password: 'Password1!' });
  const cookie = login.headers['set-cookie'] as unknown as string[];
  return (path: string) => request(guardedApp()).get(path).set('Cookie', cookie);
}

describe('requirePermission', () => {
  beforeEach(resetDb);

  it('allows a role holding the permission', async () => {
    const get = await sessionAgent('adm', 'ADMIN');
    expect((await get('/guarded')).status).toBe(200);
  });

  it('returns 403 FORBIDDEN for a role without it', async () => {
    const get = await sessionAgent('doc', 'DOCTOR');
    const res = await get('/guarded');
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });

  it('passes when the user has any one of several permissions', async () => {
    const get = await sessionAgent('doc', 'DOCTOR');
    expect((await get('/any')).status).toBe(200);
  });

  it('returns 401 without a session cookie', async () => {
    const res = await request(guardedApp()).get('/guarded');
    expect(res.status).toBe(401);
  });
});
