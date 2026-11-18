import { beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { ROLES } from '@hms/shared';
import { prisma } from '../../src/db.js';
import { resetDb, testApp } from '../../test/helpers.js';
import { runSeed } from './index.js';

const logs: string[] = [];
const seed = () => runSeed({ prisma, scale: 'small', log: (m) => logs.push(m) });

describe('seed (core module)', () => {
  beforeEach(async () => {
    await resetDb();
    await prisma.role.deleteMany();
    logs.length = 0;
  });

  it('seeds 7 roles, 7 users (one per role) and settings; users can log in', async () => {
    await seed();
    expect(await prisma.role.count()).toBe(7);
    expect(await prisma.user.count()).toBe(7);
    for (const role of ROLES) {
      expect(await prisma.user.count({ where: { role: { name: role } } })).toBe(1);
    }
    const hn = await prisma.setting.findUniqueOrThrow({ where: { key: 'hospitalName' } });
    expect(hn.value).toBe('Lanka General Hospital (Demo)');
    expect(logs.join('\n')).toContain('core');

    const app = testApp();
    const users = await prisma.user.findMany();
    expect(users.every((u) => !u.mustChangePassword)).toBe(true);
    for (const u of users) {
      const res = await request(app).post('/api/v1/auth/login').send({ username: u.username, password: 'Password1!' });
      expect(res.status).toBe(200);
    }
    const admin = await prisma.user.findFirstOrThrow({ where: { role: { name: 'ADMIN' } } });
    expect(admin.fullName).toBe('Nimal Perera');
  });

  it('is idempotent', async () => {
    await seed();
    const counts = async () => [
      await prisma.role.count(),
      await prisma.user.count(),
      await prisma.setting.count(),
    ];
    const first = await counts();
    await seed();
    expect(await counts()).toEqual(first);
  });
});
