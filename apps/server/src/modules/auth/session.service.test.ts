import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { prisma } from '../../db.js';
import { resetClock, setNow } from '../../lib/clock.js';
import { createUser, resetDb } from '../../../test/helpers.js';
import { hashToken, sessionService } from './session.service.js';
import { hashPassword, verifyPassword } from './password.js';

const T0 = new Date('2030-01-01T09:00:00Z');
const min = (n: number) => new Date(T0.getTime() + n * 60_000);

describe('sessionService', () => {
  let userId: string;
  beforeEach(async () => {
    await resetDb();
    userId = (await createUser({ username: 'svc' })).id;
    setNow(T0);
  });
  afterEach(() => resetClock());
  afterAll(() => prisma.$disconnect());

  it('creates a session storing only the sha256 of the token', async () => {
    const { token, session } = await sessionService.create(userId, { ip: '1.2.3.4' });
    expect(token.length).toBeGreaterThanOrEqual(43);
    expect(session.tokenHash).toBe(hashToken(token));
    expect(session.tokenHash).not.toBe(token);
    expect(session.idleExpiresAt).toEqual(min(15));
    expect(session.absoluteExpiresAt).toEqual(min(480));
  });

  it('slides idle expiry and returns the user with role', async () => {
    const { token } = await sessionService.create(userId);
    setNow(min(10));
    const r = await sessionService.validateAndTouch(token);
    expect(r?.session.idleExpiresAt).toEqual(min(25));
    expect(r?.user.role.name).toBe('ADMIN');
  });

  it('returns null and deletes an expired session', async () => {
    const { token } = await sessionService.create(userId);
    setNow(min(15));
    expect(await sessionService.validateAndTouch(token)).toBeNull();
    expect(await prisma.session.count()).toBe(0);
  });

  it('returns null for unknown token and for deactivated user', async () => {
    expect(await sessionService.validateAndTouch('nope')).toBeNull();
    const { token } = await sessionService.create(userId);
    await prisma.user.update({ where: { id: userId }, data: { isActive: false } });
    expect(await sessionService.validateAndTouch(token)).toBeNull();
  });

  it('revoke and revokeAllForUser delete sessions', async () => {
    const a = await sessionService.create(userId);
    await sessionService.create(userId);
    await sessionService.revoke(a.token);
    expect(await prisma.session.count()).toBe(1);
    await sessionService.revokeAllForUser(userId);
    expect(await prisma.session.count()).toBe(0);
  });
});

describe('password', () => {
  it('hashes and verifies', async () => {
    const h = await hashPassword('Secret123');
    expect(h).toMatch(/^\$2[aby]\$04\$/);
    expect(await verifyPassword('Secret123', h)).toBe(true);
    expect(await verifyPassword('wrong', h)).toBe(false);
  });
});
