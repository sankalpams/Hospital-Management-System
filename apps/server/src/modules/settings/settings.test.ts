import { beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { prisma } from '../../db.js';
import { createUser, loginAs, resetDb, testApp } from '../../../test/helpers.js';

const app = testApp();
const valid = {
  hospitalName: 'City Hospital',
  hospitalAddress: '1 Main St',
  hospitalPhone: '0112223334',
  currencyCode: 'lkr',
  currencySymbol: 'Rs.',
  taxRatePercent: 8,
  sessionTimeoutMinutes: 30,
  expiryAlertDays: 60,
};

describe('settings API', () => {
  beforeEach(resetDb);

  it('GET /settings/public returns the public subset to any signed-in user; 401 anonymous', async () => {
    expect((await request(app).get('/api/v1/settings/public')).status).toBe(401);
    const { agent } = await loginAs('NURSE', app);
    const res = await agent.get('/api/v1/settings/public');
    expect(res.status).toBe(200);
    expect(Object.keys(res.body).sort()).toEqual(
      [
        'currencyCode',
        'currencySymbol',
        'hospitalAddress',
        'hospitalName',
        'hospitalPhone',
        'sessionTimeoutMinutes',
      ].sort(),
    );
    expect(res.body.currencyCode).toBe('LKR');
  });

  it('admin GET returns all settings and PUT persists and audits before/after', async () => {
    const { agent, user } = await loginAs('ADMIN', app);
    const before = await agent.get('/api/v1/settings');
    expect(before.status).toBe(200);
    expect(before.body.expiryAlertDays).toBe(90);

    const res = await agent.put('/api/v1/settings').send(valid);
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ ...valid, currencyCode: 'LKR' });
    expect((await agent.get('/api/v1/settings')).body.hospitalName).toBe('City Hospital');
    expect((await agent.get('/api/v1/settings/public')).body.sessionTimeoutMinutes).toBe(30);

    const audit = await prisma.auditLog.findFirstOrThrow({ where: { action: 'UPDATE', entityType: 'Setting' } });
    expect(audit.userId).toBe(user.id);
    expect(audit.before).toMatchObject({ hospitalName: 'Hospital', expiryAlertDays: 90 });
    expect(audit.after).toMatchObject({ hospitalName: 'City Hospital', expiryAlertDays: 60 });
  });

  it('settings PUT rejects sessionTimeoutMinutes 3', async () => {
    const { agent } = await loginAs('ADMIN', app);
    const res = await agent.put('/api/v1/settings').send({ ...valid, sessionTimeoutMinutes: 3 });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it.each([
    ['sessionTimeoutMinutes', 4],
    ['sessionTimeoutMinutes', 121],
    ['taxRatePercent', -1],
    ['taxRatePercent', 101],
    ['expiryAlertDays', 0],
    ['expiryAlertDays', 366],
  ])('rejects %s=%s', async (key, val) => {
    const { agent } = await loginAs('ADMIN', app);
    expect((await agent.put('/api/v1/settings').send({ ...valid, [key]: val })).status).toBe(400);
    expect(await prisma.setting.count()).toBe(0);
  });

  it('accepts boundary values', async () => {
    const { agent } = await loginAs('ADMIN', app);
    const lo = { ...valid, sessionTimeoutMinutes: 5, taxRatePercent: 0, expiryAlertDays: 1 };
    const hi = { ...valid, sessionTimeoutMinutes: 120, taxRatePercent: 100, expiryAlertDays: 365 };
    expect((await agent.put('/api/v1/settings').send(lo)).status).toBe(200);
    expect((await agent.put('/api/v1/settings').send(hi)).status).toBe(200);
  });

  it('non-admin gets 403 on PUT /settings but 200 on /settings/public', async () => {
    const { agent } = await loginAs('DOCTOR', app);
    expect((await agent.put('/api/v1/settings').send(valid)).status).toBe(403);
    expect((await agent.get('/api/v1/settings')).status).toBe(403);
    expect((await agent.get('/api/v1/settings/public')).status).toBe(200);
  });

  it('GET /settings/public is allowed while mustChangePassword is set', async () => {
    await createUser({ role: 'NURSE', username: 'fresh', mustChangePassword: true });
    const agent = request.agent(app);
    expect((await agent.post('/api/v1/auth/login').send({ username: 'fresh', password: 'Password1!' })).status).toBe(200);
    expect((await agent.get('/api/v1/settings/public')).status).toBe(200);
    expect((await agent.get('/api/v1/settings')).status).toBe(403);
  });
});
