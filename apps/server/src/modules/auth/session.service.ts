import crypto from 'node:crypto';
import type { Prisma, Role as RoleRow, Session, User } from '@prisma/client';
import { clock } from '../../lib/clock.js';
import { getSetting } from '../settings/settings.service.js';
import { sessionRepository } from './session.repository.js';

export const ABSOLUTE_SESSION_MS = 8 * 60 * 60 * 1000;

export interface SessionMeta {
  ip?: string | null;
  userAgent?: string | null;
}

export type SessionUser = User & { role: RoleRow };

export const hashToken = (token: string): string =>
  crypto.createHash('sha256').update(token).digest('hex');

export const sessionService = {
  async create(
    userId: string,
    meta: SessionMeta = {},
    tx?: Prisma.TransactionClient,
  ): Promise<{ token: string; session: Session }> {
    const token = crypto.randomBytes(32).toString('base64url');
    const now = clock.now();
    const minutes = await getSetting('sessionTimeoutMinutes');
    const session = await sessionRepository.create(
      {
        tokenHash: hashToken(token),
        userId,
        createdAt: now,
        lastSeenAt: now,
        idleExpiresAt: new Date(now.getTime() + minutes * 60_000),
        absoluteExpiresAt: new Date(now.getTime() + ABSOLUTE_SESSION_MS),
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      tx,
    );
    return { token, session };
  },

  /** Returns null (and deletes the row) when the session is expired or its user is inactive. */
  async validateAndTouch(token: string): Promise<{ session: Session; user: SessionUser } | null> {
    const found = await sessionRepository.findByTokenHash(hashToken(token));
    if (!found) return null;
    const { user, ...session } = found;
    const now = clock.now();
    if (now >= session.idleExpiresAt || now >= session.absoluteExpiresAt || !user.isActive) {
      await sessionRepository.deleteById(session.id);
      return null;
    }
    const minutes = await getSetting('sessionTimeoutMinutes');
    const idleExpiresAt = new Date(now.getTime() + minutes * 60_000);
    const touched = await sessionRepository.touch(session.id, { lastSeenAt: now, idleExpiresAt });
    return { session: touched, user };
  },

  async revoke(token: string, tx?: Prisma.TransactionClient): Promise<void> {
    await sessionRepository.deleteByTokenHash(hashToken(token), tx);
  },

  async revokeAllForUser(
    userId: string,
    opts: { exceptSessionId?: string; tx?: Prisma.TransactionClient } = {},
  ): Promise<void> {
    await sessionRepository.deleteAllForUser(userId, opts.exceptSessionId, opts.tx);
  },
};

export function effectiveExpiry(s: Pick<Session, 'idleExpiresAt' | 'absoluteExpiresAt'>): Date {
  return s.idleExpiresAt < s.absoluteExpiresAt ? s.idleExpiresAt : s.absoluteExpiresAt;
}
