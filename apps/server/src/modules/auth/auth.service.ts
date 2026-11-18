import crypto from 'node:crypto';
import type { Role } from '@hms/shared';
import { prisma } from '../../db.js';
import { AppError } from '../../http/errors.js';
import { clock } from '../../lib/clock.js';
import { auditService } from '../audit/audit.service.js';
import { hashPassword, verifyPassword } from './password.js';
import { effectiveExpiry, sessionService, type SessionMeta } from './session.service.js';

export const MAX_FAILED_LOGINS = 5;
export const LOCKOUT_MS = 15 * 60_000;

// Hash of a random secret at the configured cost; compared against for unknown usernames
// so the response time matches a real failed login.
let dummyHash: Promise<string> | undefined;
const getDummyHash = () => (dummyHash ??= hashPassword(crypto.randomBytes(16).toString('hex')));

const invalidCredentials = () =>
  new AppError(401, 'INVALID_CREDENTIALS', 'Invalid username or password');

export interface PublicUser {
  id: string;
  username: string;
  fullName: string;
  role: Role;
  mustChangePassword: boolean;
}

export const authService = {
  async login(
    username: string,
    password: string,
    meta: SessionMeta,
  ): Promise<{ token: string; expiresAt: Date; user: PublicUser }> {
    const ip = meta.ip ?? null;
    const user = await prisma.user.findUnique({ where: { username }, include: { role: true } });
    const now = clock.now();

    if (user?.lockedUntil && user.lockedUntil > now) {
      const minutes = Math.ceil((user.lockedUntil.getTime() - now.getTime()) / 60_000);
      throw new AppError(423, 'ACCOUNT_LOCKED', `Account locked. Try again in ${minutes} minutes.`);
    }

    const ok = await verifyPassword(password, user?.passwordHash ?? (await getDummyHash()));

    if (!user || !ok) {
      await prisma.$transaction(async (tx) => {
        if (user) {
          // Single atomic UPDATE: concurrent failures each increment the live row value
          // (the row lock serialises them), so a stale read can never undercount.
          // An expired lock starts a fresh count.
          const lockUntil = new Date(now.getTime() + LOCKOUT_MS);
          await tx.$executeRaw`
            UPDATE "users" SET
              "failedLoginCount" = CASE WHEN "lockedUntil" IS NOT NULL AND "lockedUntil" <= ${now}
                THEN 1 ELSE "failedLoginCount" + 1 END,
              "lockedUntil" = CASE WHEN (CASE WHEN "lockedUntil" IS NOT NULL AND "lockedUntil" <= ${now}
                THEN 1 ELSE "failedLoginCount" + 1 END) >= ${MAX_FAILED_LOGINS}
                THEN ${lockUntil} ELSE NULL END
            WHERE "id" = ${user.id}`;
        }
        await auditService.record(tx, {
          userId: user?.id ?? null,
          action: 'LOGIN_FAILED',
          entityType: 'Session',
          after: { username },
          ip,
        });
      });
      throw invalidCredentials();
    }

    if (!user.isActive) {
      throw new AppError(403, 'ACCOUNT_INACTIVE', 'This account is inactive');
    }

    const { token, expiresAt } = await prisma.$transaction(async (tx) => {
      await tx.user.update({
        where: { id: user.id },
        data: { failedLoginCount: 0, lockedUntil: null, lastLoginAt: now },
      });
      const created = await sessionService.create(user.id, meta, tx);
      await auditService.record(tx, {
        userId: user.id,
        action: 'LOGIN',
        entityType: 'Session',
        entityId: created.session.id,
        ip,
      });
      return { token: created.token, expiresAt: effectiveExpiry(created.session) };
    });

    return {
      token,
      expiresAt,
      user: {
        id: user.id,
        username: user.username,
        fullName: user.fullName,
        role: user.role.name as Role,
        mustChangePassword: user.mustChangePassword,
      },
    };
  },

  async logout(userId: string, token: string, sessionId: string, ip: string | null) {
    await prisma.$transaction(async (tx) => {
      await sessionService.revoke(token, tx);
      await auditService.record(tx, {
        userId,
        action: 'LOGOUT',
        entityType: 'Session',
        entityId: sessionId,
        ip,
      });
    });
  },

  async changePassword(
    userId: string,
    sessionId: string,
    currentPassword: string,
    newPassword: string,
    ip: string | null,
  ) {
    const user = await prisma.user.findUniqueOrThrow({ where: { id: userId } });
    if (!(await verifyPassword(currentPassword, user.passwordHash))) {
      throw new AppError(400, 'INVALID_CURRENT_PASSWORD', 'Current password is incorrect');
    }
    const passwordHash = await hashPassword(newPassword);
    await prisma.$transaction(async (tx) => {
      await tx.user.update({
        where: { id: userId },
        data: { passwordHash, mustChangePassword: false },
      });
      // Other devices must re-authenticate with the new password.
      await sessionService.revokeAllForUser(userId, { tx, exceptSessionId: sessionId });
      await auditService.record(tx, {
        userId,
        action: 'UPDATE',
        entityType: 'User',
        entityId: userId,
        before: { mustChangePassword: user.mustChangePassword },
        after: { passwordChanged: true, mustChangePassword: false },
        ip,
      });
    });
  },
};
