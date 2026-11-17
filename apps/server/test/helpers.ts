import type { Express } from 'express';
import request from 'supertest';
import type { RoleName } from '@prisma/client';
import { prisma } from '../src/db.js';
import { createApp } from '../src/app.js';
import { hashPassword } from '../src/modules/auth/password.js';

/** Truncate every table except Prisma's migration bookkeeping. */
export async function resetDb(): Promise<void> {
  const rows = await prisma.$queryRaw<{ tablename: string }[]>`
    SELECT tablename FROM pg_tables
    WHERE schemaname = 'public' AND tablename <> '_prisma_migrations'`;
  if (rows.length === 0) return;
  const list = rows.map((r) => `"public"."${r.tablename}"`).join(', ');
  await prisma.$executeRawUnsafe(`TRUNCATE TABLE ${list} RESTART IDENTITY CASCADE`);
}

/** App with the login rate limiter disabled (ruling R2). */
export const testApp = () => createApp({ loginRateLimit: false });

/** Test fixture: creates a user (and upserts its Role row). Task 7 builds the real seed. */
export async function createUser(opts: {
  role?: RoleName;
  username: string;
  password?: string;
  fullName?: string;
  isActive?: boolean;
  mustChangePassword?: boolean;
}) {
  const role = opts.role ?? 'ADMIN';
  const roleRow = await prisma.role.upsert({
    where: { name: role },
    update: {},
    create: { name: role, description: role },
  });
  return prisma.user.create({
    data: {
      username: opts.username,
      passwordHash: await hashPassword(opts.password ?? 'Password1!'),
      fullName: opts.fullName ?? opts.username,
      roleId: roleRow.id,
      isActive: opts.isActive ?? true,
      mustChangePassword: opts.mustChangePassword ?? false,
    },
  });
}

/** Creates a user with the given role and returns a supertest agent holding its session cookie. */
export async function loginAs(role: RoleName, app: Express = testApp()) {
  const username = `${role.toLowerCase()}_user`;
  const password = 'Password1!';
  const user = await createUser({ role, username, password });
  const agent = request.agent(app);
  const res = await agent.post('/api/v1/auth/login').send({ username, password });
  if (res.status !== 200) throw new Error(`loginAs(${role}) failed: ${res.status}`);
  return { agent, user };
}
