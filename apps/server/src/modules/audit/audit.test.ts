import { beforeEach, describe, expect, it } from 'vitest';
import { prisma } from '../../db.js';
import { loginAs, resetDb, testApp } from '../../../test/helpers.js';

const URL = '/api/v1/audit-logs';
const app = testApp();

describe('audit log API', () => {
  beforeEach(resetDb);

  it('lists newest first, filters by action and date range, and pages', async () => {
    const { agent, user } = await loginAs('ADMIN', app); // writes one LOGIN row
    await prisma.auditLog.createMany({
      data: [1, 2, 3].map((n) => ({
        userId: user.id,
        action: 'UPDATE' as const,
        entityType: 'Patient',
        entityId: `p${n}`,
        createdAt: new Date(`2020-01-0${n}T00:00:00Z`),
      })),
    });

    const all = await agent.get(URL);
    expect(all.status).toBe(200);
    expect(all.body).toMatchObject({ page: 1, pageSize: 50, total: 4 });
    expect(all.body.items[0].action).toBe('LOGIN');
    expect(all.body.items[0].user).toEqual({ username: 'admin_user', fullName: 'admin_user' });

    const login = await agent.get(`${URL}?action=LOGIN`);
    expect(login.body.total).toBe(1);

    const range = await agent.get(`${URL}?from=2020-01-02T00:00:00Z&to=2020-01-03T23:59:59Z`);
    expect(range.body.items.map((i: { entityId: string }) => i.entityId)).toEqual(['p3', 'p2']);

    const p2 = await agent.get(`${URL}?action=UPDATE&page=2&pageSize=2`);
    expect(p2.body).toMatchObject({ page: 2, pageSize: 2, total: 3 });
    expect(p2.body.items).toHaveLength(1);
    expect(p2.body.items[0].entityId).toBe('p1');

    const byEntity = await agent.get(`${URL}?entityType=Patient&entityId=p2`);
    expect(byEntity.body.total).toBe(1);

    expect((await agent.get(`${URL}?pageSize=201`)).status).toBe(400);
  });

  it('treats date-only from/to as whole Colombo days', async () => {
    const { agent } = await loginAs('ADMIN', app);
    await prisma.auditLog.deleteMany();
    const rows: [string, string][] = [
      ['before', '2020-01-01T18:29:00Z'], // 23:59 Colombo on 01-01, excluded
      ['fromDay', '2020-01-01T19:00:00Z'], // 00:30 Colombo on 01-02, included
      ['toDay', '2020-01-03T17:30:00Z'], // 23:00 Colombo on 01-03, included
      ['after', '2020-01-03T19:00:00Z'], // 00:30 Colombo on 01-04, excluded
    ];
    await prisma.auditLog.createMany({
      data: rows.map(([id, t]) => ({
        action: 'UPDATE' as const,
        entityType: 'Patient',
        entityId: id,
        createdAt: new Date(t),
      })),
    });
    const res = await agent.get(`${URL}?from=2020-01-02&to=2020-01-03`);
    expect(res.status).toBe(200);
    expect(res.body.items.map((i: { entityId: string }) => i.entityId)).toEqual([
      'toDay',
      'fromDay',
    ]);
  });

  it.each([
    'from=2020-02-30',
    'to=2020-13-01',
    'to=2021-02-29',
    'from=2020-02-30T10:00:00Z',
    'to=2020-04-31T10:00:00Z',
  ])('rejects invalid calendar date %s with 400', async (qs) => {
    const { agent } = await loginAs('ADMIN', app);
    const res = await agent.get(`${URL}?${qs}`);
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('accepts a leap-day date', async () => {
    const { agent } = await loginAs('ADMIN', app);
    expect((await agent.get(`${URL}?from=2020-02-29&to=2020-02-29`)).status).toBe(200);
    expect((await agent.get(`${URL}?to=2020-02-29T10:00:00Z`)).status).toBe(200);
  });

  it('forbids a doctor', async () => {
    const { agent } = await loginAs('DOCTOR', app);
    const res = await agent.get(URL);
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });

  it('has no update or delete routes', async () => {
    const { agent } = await loginAs('ADMIN', app);
    expect((await agent.put(`${URL}/x`).send({})).status).toBe(404);
    expect((await agent.delete(`${URL}/x`)).status).toBe(404);
    expect((await agent.put(URL).send({})).status).toBe(404);
    expect((await agent.delete(URL)).status).toBe(404);
  });
});
