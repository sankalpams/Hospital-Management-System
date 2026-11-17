import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import express from 'express';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import { createApp } from '../../app.js';
import { prisma } from '../../db.js';
import { errorHandler } from '../../http/errorHandler.js';
import { requireAuth } from '../../http/requireAuth.js';
import { resetClock, setNow } from '../../lib/clock.js';
import { createUser, resetDb, testApp } from '../../../test/helpers.js';
import { hashToken } from './session.service.js';

const T0 = new Date('2030-01-01T09:00:00Z');
const MIN = 60_000;
const at = (ms: number) => new Date(T0.getTime() + ms);
const PW = 'Password1!';
const L = '/api/v1/auth/login';
const CP = '/api/v1/auth/change-password';

const app = testApp();
const agent = () => request.agent(app);
const loginAs = (a: ReturnType<typeof agent>, username = 'admin', password = PW) =>
  a.post(L).send({ username, password });
const cookiesOf = (res: request.Response) => res.headers['set-cookie'] as unknown as string[];
const fail = (username = 'admin') => request(app).post(L).send({ username, password: 'Wrong123' });

describe('auth', () => {
  beforeEach(async () => {
    await resetDb();
    await createUser({ username: 'admin', password: PW });
    setNow(T0);
  });
  afterEach(() => resetClock());
  afterAll(() => prisma.$disconnect());

  it('logs in with trimmed/uppercased username and sets httpOnly hms_sid', async () => {
    const res = await request(app).post(L).send({ username: ' Admin ', password: PW });
    expect(res.status).toBe(200);
    expect(res.body.user).toMatchObject({ username: 'admin', role: 'ADMIN', mustChangePassword: false });
    expect(JSON.stringify(res.body)).not.toMatch(/passwordHash/);
    const cookie = cookiesOf(res).find((c) => c.startsWith('hms_sid='))!;
    expect(cookie).toMatch(/HttpOnly/i);
    expect(cookie).toMatch(/SameSite=Lax/i);
    expect(cookie).toMatch(/Path=\//);
    expect(cookie).not.toMatch(/Secure/i);
  });

  it('returns identical 401 for unknown user and wrong password', async () => {
    const a = await request(app).post(L).send({ username: 'ghost', password: PW });
    const b = await fail();
    expect(a.status).toBe(401);
    expect(b.status).toBe(401);
    expect(a.body).toEqual(b.body);
    expect(a.body.error).toEqual({ code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' });
  });

  it('locks after 5 failures for 15 min, then allows login after', async () => {
    for (let i = 0; i < 5; i++) expect((await fail()).status).toBe(401);
    const locked = await request(app).post(L).send({ username: 'admin', password: PW });
    expect(locked.status).toBe(423);
    expect(locked.body.error.code).toBe('ACCOUNT_LOCKED');
    expect(locked.body.error.message).toBe('Account locked. Try again in 15 minutes.');
    setNow(at(15 * MIN));
    expect((await request(app).post(L).send({ username: 'admin', password: PW })).status).toBe(200);
  });

  it('counts concurrent wrong passwords atomically so the lockout still triggers', async () => {
    const results = await Promise.all(Array.from({ length: 8 }, () => fail()));
    expect(results.every((r) => r.status === 401)).toBe(true);
    const u = await prisma.user.findUniqueOrThrow({ where: { username: 'admin' } });
    expect(u.failedLoginCount).toBe(8);
    const locked = await request(app).post(L).send({ username: 'admin', password: PW });
    expect(locked.status).toBe(423);
  });

  it('starts a fresh failure count after a lock expires', async () => {
    for (let i = 0; i < 5; i++) await fail();
    setNow(at(16 * MIN));
    expect((await fail()).status).toBe(401);
    expect((await request(app).post(L).send({ username: 'admin', password: PW })).status).toBe(200);
  });

  it('resets failedLoginCount on success', async () => {
    for (let i = 0; i < 3; i++) await fail();
    expect((await prisma.user.findUniqueOrThrow({ where: { username: 'admin' } })).failedLoginCount).toBe(3);
    await loginAs(agent());
    const u = await prisma.user.findUniqueOrThrow({ where: { username: 'admin' } });
    expect(u.failedLoginCount).toBe(0);
    expect(u.lastLoginAt).toEqual(T0);
  });

  it('rejects inactive user with 403 ACCOUNT_INACTIVE', async () => {
    await createUser({ username: 'gone', isActive: false });
    const res = await request(app).post(L).send({ username: 'gone', password: PW });
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ACCOUNT_INACTIVE');
    expect(await prisma.session.count()).toBe(0);
  });

  it('GET /auth/me 401 without cookie', async () => {
    const res = await request(app).get('/api/v1/auth/me');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('GET /auth/me returns user, expiresAt and X-Session-Expires-At', async () => {
    const a = agent();
    await loginAs(a);
    setNow(at(5 * MIN));
    const res = await a.get('/api/v1/auth/me');
    expect(res.status).toBe(200);
    expect(res.body.user.username).toBe('admin');
    expect(res.body.expiresAt).toBe(at(20 * MIN).toISOString());
    expect(res.headers['x-session-expires-at']).toBe(at(20 * MIN).toISOString());
  });

  it('expires session after 15 idle minutes', async () => {
    const a = agent();
    await loginAs(a);
    setNow(at(15 * MIN + 1000));
    const res = await a.get('/api/v1/auth/me');
    expect(res.status).toBe(401);
    expect(String(res.headers['set-cookie'])).toMatch(/hms_sid=;/);
  });

  it('slides idle expiry on activity', async () => {
    const a = agent();
    await loginAs(a);
    for (let m = 10; m <= 60; m += 10) {
      setNow(at(m * MIN));
      expect((await a.get('/api/v1/auth/me')).status).toBe(200);
    }
  });

  it('enforces 8h absolute limit even with activity', async () => {
    const a = agent();
    await loginAs(a);
    for (let m = 10; m <= 470; m += 10) {
      setNow(at(m * MIN));
      expect((await a.get('/api/v1/auth/me')).status).toBe(200);
    }
    setNow(at(475 * MIN));
    const near = await a.get('/api/v1/auth/me');
    expect(near.status).toBe(200);
    expect(near.headers['x-session-expires-at']).toBe(at(480 * MIN).toISOString());
    setNow(at(480 * MIN + 1000));
    expect((await a.get('/api/v1/auth/me')).status).toBe(401);
  });

  it('uses sessionTimeoutMinutes setting', async () => {
    await prisma.setting.create({ data: { key: 'sessionTimeoutMinutes', value: 30 } });
    const a = agent();
    await loginAs(a);
    setNow(at(20 * MIN));
    expect((await a.get('/api/v1/auth/me')).status).toBe(200);
  });

  it('logout revokes session; reused cookie -> 401', async () => {
    const a = agent();
    const login = await loginAs(a);
    const raw = cookiesOf(login)[0]!.split(';')[0]!;
    const out = await a.post('/api/v1/auth/logout');
    expect(out.status).toBe(204);
    expect(await prisma.session.count()).toBe(0);
    const replay = await request(app).get('/api/v1/auth/me').set('Cookie', raw);
    expect(replay.status).toBe(401);
  });

  it('change-password requires correct current password and policy; clears mustChangePassword', async () => {
    await createUser({ username: 'newbie', password: 'Temp1234', mustChangePassword: true });
    const a = agent();
    await loginAs(a, 'newbie', 'Temp1234');
    const wrong = await a.post(CP).send({ currentPassword: 'Nope1234', newPassword: 'Better123' });
    expect(wrong.status).toBe(400);
    expect(wrong.body.error.code).toBe('INVALID_CURRENT_PASSWORD');
    const weak = await a.post(CP).send({ currentPassword: 'Temp1234', newPassword: 'short' });
    expect(weak.status).toBe(400);
    expect(weak.body.error.code).toBe('VALIDATION_ERROR');
    const ok = await a.post(CP).send({ currentPassword: 'Temp1234', newPassword: 'Better123' });
    expect(ok.status).toBe(204);
    const u = await prisma.user.findUniqueOrThrow({ where: { username: 'newbie' } });
    expect(u.mustChangePassword).toBe(false);
    expect((await a.get('/api/v1/auth/me')).body.user.mustChangePassword).toBe(false);
    expect((await request(app).post(L).send({ username: 'newbie', password: 'Better123' })).status).toBe(200);
    expect((await request(app).post(L).send({ username: 'newbie', password: 'Temp1234' })).status).toBe(401);
    const audit = await prisma.auditLog.findFirstOrThrow({ where: { action: 'UPDATE', userId: u.id } });
    expect(JSON.stringify(audit)).not.toMatch(/Better123|\$2/);
  });

  it('change-password revokes other sessions of the user but keeps the current one', async () => {
    const a = agent();
    const b = agent();
    await loginAs(a);
    await loginAs(b);
    await a.post(CP).send({ currentPassword: PW, newPassword: 'Better123' });
    expect((await a.get('/api/v1/auth/me')).status).toBe(200);
    expect((await b.get('/api/v1/auth/me')).status).toBe(401);
  });

  it('mustChangePassword user gets 403 PASSWORD_CHANGE_REQUIRED on other routes', async () => {
    await createUser({ username: 'newbie', password: 'Temp1234', mustChangePassword: true });
    const a = agent();
    const login = await loginAs(a, 'newbie', 'Temp1234');
    expect(login.status).toBe(200);
    expect(login.body.user.mustChangePassword).toBe(true);
    expect((await a.get('/api/v1/auth/me')).status).toBe(200);
    // No other authenticated routes exist yet; exercise the middleware on a probe app.
    const probe = express();
    probe.use(cookieParser());
    probe.get('/x', requireAuth(), (_req, res) => void res.json({ ok: true }));
    probe.use(errorHandler);
    const res = await request(probe).get('/x').set('Cookie', cookiesOf(login)[0]!.split(';')[0]!);
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('PASSWORD_CHANGE_REQUIRED');
    expect((await a.post('/api/v1/auth/logout')).status).toBe(204);
  });

  it('writes LOGIN, LOGIN_FAILED (userId null for unknown user, username in after), LOGOUT audit rows', async () => {
    const admin = await prisma.user.findUniqueOrThrow({ where: { username: 'admin' } });
    await request(app).post(L).send({ username: 'Ghost', password: PW });
    await fail();
    const a = agent();
    await loginAs(a);
    await a.post('/api/v1/auth/logout');
    const rows = await prisma.auditLog.findMany({ orderBy: { id: 'asc' } });
    expect(rows.map((r) => r.action).sort()).toEqual(['LOGIN', 'LOGIN_FAILED', 'LOGIN_FAILED', 'LOGOUT']);
    const failed = rows.filter((r) => r.action === 'LOGIN_FAILED');
    expect(failed.find((r) => r.userId === null)?.after).toEqual({ username: 'ghost' });
    expect(failed.find((r) => r.userId === admin.id)?.after).toEqual({ username: 'admin' });
    expect(rows.find((r) => r.action === 'LOGIN')?.userId).toBe(admin.id);
    expect(rows.find((r) => r.action === 'LOGOUT')?.userId).toBe(admin.id);
    expect(JSON.stringify(rows)).not.toMatch(/Wrong123|Password1/);
  });

  it('stores only sha256 hash of token in sessions table', async () => {
    const res = await request(app).post(L).send({ username: 'admin', password: PW });
    const token = decodeURIComponent(cookiesOf(res)[0]!.split(';')[0]!.split('=')[1]!);
    const s = await prisma.session.findFirstOrThrow();
    expect(s.tokenHash).toBe(hashToken(token));
    expect(s.tokenHash).toMatch(/^[0-9a-f]{64}$/);
    expect(s.tokenHash).not.toContain(token);
  });
});

describe('login rate limit', () => {
  beforeEach(async () => {
    await resetDb();
  });
  afterAll(() => prisma.$disconnect());

  it('returns 429 on the 21st login attempt within a minute', async () => {
    const limited = createApp({ loginRateLimit: 20 });
    for (let i = 0; i < 20; i++) {
      expect((await request(limited).post(L).send({ username: 'ghost', password: PW })).status).toBe(401);
    }
    const res = await request(limited).post(L).send({ username: 'ghost', password: PW });
    expect(res.status).toBe(429);
    expect(res.body.error.code).toBe('RATE_LIMITED');
  });
});
