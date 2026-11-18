import { beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { passwordSchema } from '@hms/shared';
import { prisma } from '../../db.js';
import { createUser, loginAs, resetDb, testApp } from '../../../test/helpers.js';
import { Prisma } from '@prisma/client';
import { generateTemporaryPassword, isUsernameViolation, usersService } from './users.service.js';

const URL = '/api/v1/users';
const app = testApp();

async function login(username: string, password: string) {
  const agent = request.agent(app);
  const res = await agent.post('/api/v1/auth/login').send({ username, password });
  return { agent, res };
}

describe('generateTemporaryPassword', () => {
  it('is 12 chars, passes passwordSchema, and varies', () => {
    const seen = new Set<string>();
    for (let i = 0; i < 2000; i++) {
      const pw = generateTemporaryPassword();
      expect(pw).toHaveLength(12);
      expect(passwordSchema.safeParse(pw).success).toBe(true);
      seen.add(pw);
    }
    expect(seen.size).toBe(2000);
  });
});

describe('users admin API', () => {
  beforeEach(resetDb);

  it('requires user:manage (403 for non-admin, 401 anonymous)', async () => {
    const { agent } = await loginAs('RECEPTIONIST', app);
    expect((await agent.get(URL)).status).toBe(403);
    expect((await agent.post(URL).send({ username: 'abc', fullName: 'A', role: 'NURSE' })).status).toBe(403);
    expect((await agent.patch(`${URL}/x`).send({ fullName: 'B' })).status).toBe(403);
    expect((await agent.post(`${URL}/x/reset-password`)).status).toBe(403);
    expect((await request(app).get(URL)).status).toBe(401);
  });

  it('lists users without passwordHash, with search/role/isActive filters and paging', async () => {
    const { agent } = await loginAs('ADMIN', app);
    await createUser({ role: 'NURSE', username: 'nina', fullName: 'Nina Perera' });
    await createUser({ role: 'NURSE', username: 'nuwan', fullName: 'Nuwan K', isActive: false });
    await createUser({ role: 'DOCTOR', username: 'doc1', fullName: 'Dr One' });

    const all = await agent.get(URL);
    expect(all.status).toBe(200);
    expect(all.body).toMatchObject({ page: 1, pageSize: 50, total: 4 });
    expect(JSON.stringify(all.body)).not.toContain('passwordHash');
    expect(all.body.items[0]).toHaveProperty('role', expect.any(String));

    expect((await agent.get(`${URL}?role=NURSE`)).body.total).toBe(2);
    expect((await agent.get(`${URL}?role=NURSE&isActive=false`)).body.items[0].username).toBe('nuwan');
    expect(
      (await agent.get(`${URL}?search=perera`)).body.items.map((u: { username: string }) => u.username),
    ).toEqual(['nina']);
    const p = await agent.get(`${URL}?pageSize=2&page=2`);
    expect(p.body.items).toHaveLength(2);
    expect((await agent.get(`${URL}?role=BOGUS`)).status).toBe(400);
  });

  it('creates a user with a one-time temporary password, forces change, and audits', async () => {
    const { agent, user: admin } = await loginAs('ADMIN', app);
    const res = await agent
      .post(URL)
      .send({ username: 'Newbie', fullName: 'New Bie', email: 'n@x.lk', role: 'NURSE' });
    expect(res.status).toBe(201);
    expect(res.body.user).toMatchObject({
      username: 'newbie',
      fullName: 'New Bie',
      role: 'NURSE',
      isActive: true,
      mustChangePassword: true,
    });
    expect(res.body.user).not.toHaveProperty('passwordHash');
    expect(passwordSchema.safeParse(res.body.temporaryPassword).success).toBe(true);
    expect(JSON.stringify(res.body)).not.toContain('passwordHash');

    const row = await prisma.user.findUniqueOrThrow({ where: { username: 'newbie' } });
    expect(row.mustChangePassword).toBe(true);
    expect(row.passwordHash).not.toBe(res.body.temporaryPassword);

    const audit = await prisma.auditLog.findFirstOrThrow({ where: { action: 'CREATE', entityType: 'User' } });
    expect(audit).toMatchObject({ userId: admin.id, entityId: row.id });
    expect(JSON.stringify(audit)).not.toContain(res.body.temporaryPassword);
    expect(JSON.stringify(audit)).not.toContain('passwordHash');
    expect(audit.after).toMatchObject({ username: 'newbie', role: 'NURSE' });

    const list = await agent.get(URL);
    expect(JSON.stringify(list.body)).not.toContain(res.body.temporaryPassword);
  });

  it('temporary password logs in and forces change', async () => {
    const { agent } = await loginAs('ADMIN', app);
    const created = await agent.post(URL).send({ username: 'fresh', fullName: 'Fresh', role: 'DOCTOR' });
    const tmp = created.body.temporaryPassword as string;

    const { agent: a2, res } = await login('fresh', tmp);
    expect(res.status).toBe(200);
    expect(JSON.stringify(res.body)).toContain('"mustChangePassword":true');
    const blocked = await a2.get(URL);
    expect(blocked.status).toBe(403);
    expect(blocked.body.error.code).toBe('PASSWORD_CHANGE_REQUIRED');
    const change = await a2
      .post('/api/v1/auth/change-password')
      .send({ currentPassword: tmp, newPassword: 'BrandNew123' });
    expect(change.status).toBeLessThan(300);
    expect((await prisma.user.findUniqueOrThrow({ where: { username: 'fresh' } })).mustChangePassword).toBe(
      false,
    );
  });

  it('rejects duplicate usernames with 409 USERNAME_TAKEN and invalid bodies with 400', async () => {
    const { agent } = await loginAs('ADMIN', app);
    await createUser({ role: 'NURSE', username: 'taken' });
    const dup = await agent.post(URL).send({ username: 'TAKEN', fullName: 'X', role: 'NURSE' });
    expect(dup.status).toBe(409);
    expect(dup.body.error.code).toBe('USERNAME_TAKEN');
    expect((await agent.post(URL).send({ username: 'a', fullName: 'X', role: 'NURSE' })).status).toBe(400);
    expect((await agent.post(URL).send({ username: 'okname', fullName: 'X', role: 'NOPE' })).status).toBe(400);
  });

  it('patches fields and audits UPDATE with a before/after diff', async () => {
    const { agent, user: admin } = await loginAs('ADMIN', app);
    const nurse = await createUser({ role: 'NURSE', username: 'nurse1', fullName: 'Old Name' });
    const res = await agent.patch(`${URL}/${nurse.id}`).send({ fullName: 'New Name', role: 'DOCTOR' });
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ fullName: 'New Name', role: 'DOCTOR' });
    expect(res.body).not.toHaveProperty('passwordHash');

    const audit = await prisma.auditLog.findFirstOrThrow({
      where: { action: 'UPDATE', entityType: 'User', entityId: nurse.id },
    });
    expect(audit.userId).toBe(admin.id);
    expect(audit.before).toEqual({ fullName: 'Old Name', role: 'NURSE' });
    expect(audit.after).toEqual({ fullName: 'New Name', role: 'DOCTOR' });

    expect((await agent.patch(`${URL}/nope`).send({ fullName: 'Z' })).status).toBe(404);
    expect((await agent.patch(`${URL}/${nurse.id}`).send({ role: 'BAD' })).status).toBe(400);
  });

  it('deactivated user with open session gets 401 on next request', async () => {
    const { agent } = await loginAs('ADMIN', app);
    const target = await createUser({ role: 'NURSE', username: 'victim', password: 'Password1!' });
    const { agent: victim } = await login('victim', 'Password1!');
    expect((await victim.get('/api/v1/auth/me')).status).toBe(200);
    expect(await prisma.session.count({ where: { userId: target.id } })).toBe(1);

    const res = await agent.patch(`${URL}/${target.id}`).send({ isActive: false });
    expect(res.status).toBe(200);
    expect(await prisma.session.count({ where: { userId: target.id } })).toBe(0);
    expect((await victim.get('/api/v1/auth/me')).status).toBe(401);
  });

  it('reset-password returns a one-time temp password, sets mustChangePassword, revokes sessions, audits', async () => {
    const { agent, user: admin } = await loginAs('ADMIN', app);
    const target = await createUser({ role: 'DOCTOR', username: 'doc', password: 'Password1!' });
    const { agent: docAgent } = await login('doc', 'Password1!');
    expect((await docAgent.get('/api/v1/auth/me')).status).toBe(200);

    const res = await agent.post(`${URL}/${target.id}/reset-password`);
    expect(res.status).toBe(200);
    expect(Object.keys(res.body)).toEqual(['temporaryPassword']);
    expect(passwordSchema.safeParse(res.body.temporaryPassword).success).toBe(true);

    const row = await prisma.user.findUniqueOrThrow({ where: { id: target.id } });
    expect(row.mustChangePassword).toBe(true);
    expect(await prisma.session.count({ where: { userId: target.id } })).toBe(0);
    expect((await docAgent.get('/api/v1/auth/me')).status).toBe(401);
    expect((await login('doc', 'Password1!')).res.status).toBe(401);
    expect((await login('doc', res.body.temporaryPassword)).res.status).toBe(200);

    const audit = await prisma.auditLog.findFirstOrThrow({
      where: { action: 'UPDATE', entityType: 'User', entityId: target.id },
    });
    expect(audit.userId).toBe(admin.id);
    expect(JSON.stringify(audit)).not.toContain(res.body.temporaryPassword);
    expect(JSON.stringify(audit)).not.toContain('passwordHash');

    expect((await agent.post(`${URL}/nope/reset-password`)).status).toBe(404);
  });

  it('admin cannot deactivate self → 409 LAST_ADMIN', async () => {
    const { agent, user } = await loginAs('ADMIN', app);
    await createUser({ role: 'ADMIN', username: 'admin2' }); // another admin exists; still refused
    const res = await agent.patch(`${URL}/${user.id}`).send({ isActive: false });
    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('LAST_ADMIN');
    expect((await prisma.user.findUniqueOrThrow({ where: { id: user.id } })).isActive).toBe(true);
  });

  it('admin cannot change own role, but unchanged values and other edits are fine', async () => {
    const { agent, user } = await loginAs('ADMIN', app);
    const own = await agent.patch(`${URL}/${user.id}`).send({ role: 'DOCTOR' });
    expect(own.status).toBe(409);
    expect(own.body.error.code).toBe('LAST_ADMIN');
    expect((await agent.patch(`${URL}/${user.id}`).send({ fullName: 'Me' })).status).toBe(200);
    expect((await agent.patch(`${URL}/${user.id}`).send({ isActive: true, role: 'ADMIN' })).status).toBe(200);
  });

  it('cannot demote the only active admin', async () => {
    // The actor is a non-admin id (e.g. a stale actor), so only the zero-admin rule can fire.
    const onlyAdmin = await createUser({ role: 'ADMIN', username: 'solo' });
    const actor = await createUser({ role: 'DOCTOR', username: 'actor' });
    await expect(
      usersService.update(actor.id, onlyAdmin.id, { role: 'NURSE' }, null),
    ).rejects.toMatchObject({ status: 409, code: 'LAST_ADMIN' });
    await expect(
      usersService.update(actor.id, onlyAdmin.id, { isActive: false }, null),
    ).rejects.toMatchObject({ status: 409, code: 'LAST_ADMIN' });
    const row = await prisma.user.findUniqueOrThrow({ where: { id: onlyAdmin.id }, include: { role: true } });
    expect(row.isActive).toBe(true);
    expect(row.role.name).toBe('ADMIN');
    expect(await prisma.auditLog.count({ where: { action: 'UPDATE' } })).toBe(0);
  });

  it('can deactivate another admin when an active admin remains', async () => {
    const { agent } = await loginAs('ADMIN', app);
    const other = await createUser({ role: 'ADMIN', username: 'admin2' });
    const res = await agent.patch(`${URL}/${other.id}`).send({ isActive: false });
    expect(res.status).toBe(200);
    expect(res.body.isActive).toBe(false);
  });
});

describe('users R10 follow-ups', () => {
  beforeEach(resetDb);

  it('create/update with a missing role row returns 400 instead of inventing the role', async () => {
    const { agent } = await loginAs('ADMIN', app);
    await prisma.role.deleteMany({ where: { name: 'NURSE' } });
    const res = await agent.post(URL).send({ username: 'nina', fullName: 'Nina P', role: 'NURSE' });
    expect(res.status).toBe(400);
    expect(await prisma.role.count({ where: { name: 'NURSE' } })).toBe(0);
    const other = await createUser({ role: 'DOCTOR', username: 'doc9' });
    const upd = await agent.patch(`${URL}/${other.id}`).send({ role: 'NURSE' });
    expect(upd.status).toBe(400);
  });

  it('isUsernameViolation only matches P2002 on username', () => {
    const mk = (target: unknown) =>
      new Prisma.PrismaClientKnownRequestError('x', { code: 'P2002', clientVersion: '6', meta: { target } });
    expect(isUsernameViolation(mk(['username']))).toBe(true);
    expect(isUsernameViolation(mk('users_username_key'))).toBe(true);
    expect(isUsernameViolation(mk(['email']))).toBe(false);
    expect(isUsernameViolation(new Error('x'))).toBe(false);
  });

  it('concurrent mutual deactivation of two admins leaves exactly one active admin', async () => {
    const a = await createUser({ role: 'ADMIN', username: 'adminA' });
    const b = await createUser({ role: 'ADMIN', username: 'adminB' });
    const results = await Promise.allSettled([
      usersService.update(a.id, b.id, { isActive: false }, null),
      usersService.update(b.id, a.id, { isActive: false }, null),
    ]);
    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
    const rejected = results.filter((r): r is PromiseRejectedResult => r.status === 'rejected');
    expect(rejected).toHaveLength(1);
    expect(rejected[0]?.reason).toMatchObject({ status: 409, code: 'LAST_ADMIN' });
    expect(await prisma.user.count({ where: { isActive: true, role: { name: 'ADMIN' } } })).toBe(1);
  });
});
