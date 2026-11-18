import crypto from 'node:crypto';
import { Prisma } from '@prisma/client';
import type { CreateUserInput, Role, UpdateUserInput } from '@hms/shared';
import { prisma } from '../../db.js';
import { AppError, notFound } from '../../http/errors.js';
import type { Paged } from '../../http/paging.js';
import { auditService } from '../audit/audit.service.js';
import { hashPassword } from '../auth/password.js';
import { sessionService } from '../auth/session.service.js';
import { usersRepository, type UserListFilter, type UserRow } from './users.repository.js';

export interface UserView {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  isActive: boolean;
  mustChangePassword: boolean;
  lastLoginAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

const toView = ({ role, ...rest }: UserRow): UserView => ({ ...rest, role: role.name as Role });

const usernameTaken = () => new AppError(409, 'USERNAME_TAKEN', 'Username is already taken');
const lastAdmin = (message: string) => new AppError(409, 'LAST_ADMIN', message);

const LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const DIGITS = '23456789';
const ALPHABET = LETTERS + DIGITS;
const TEMP_PASSWORD_LENGTH = 12;

const pick = (chars: string): string => chars[crypto.randomInt(chars.length)] as string;

/** 12 crypto-random chars (no ambiguous glyphs) with at least one letter and one digit. */
export function generateTemporaryPassword(): string {
  const chars = [pick(LETTERS), pick(DIGITS)];
  while (chars.length < TEMP_PASSWORD_LENGTH) chars.push(pick(ALPHABET));
  for (let i = chars.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j] as string, chars[i] as string];
  }
  return chars.join('');
}

const isUniqueViolation = (err: unknown) =>
  err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2002';

const TRACKED = ['fullName', 'email', 'role', 'isActive'] as const;

export const usersService = {
  async list(
    filter: UserListFilter,
    paging: { page: number; pageSize: number; skip: number; take: number },
  ): Promise<Paged<UserView>> {
    const { items, total } = await usersRepository.list(filter, paging.skip, paging.take);
    return { items: items.map(toView), page: paging.page, pageSize: paging.pageSize, total };
  },

  async create(
    actorId: string,
    input: CreateUserInput,
    ip: string | null,
  ): Promise<{ user: UserView; temporaryPassword: string }> {
    if (await usersRepository.findByUsername(input.username)) throw usernameTaken();
    const temporaryPassword = generateTemporaryPassword();
    const passwordHash = await hashPassword(temporaryPassword);
    try {
      const user = await prisma.$transaction(async (tx) => {
        const role = await usersRepository.ensureRole(input.role, tx);
        const created = await usersRepository.create(
          {
            username: input.username,
            fullName: input.fullName,
            email: input.email ?? null,
            roleId: role.id,
            passwordHash,
            mustChangePassword: true,
          },
          tx,
        );
        await auditService.record(tx, {
          userId: actorId,
          action: 'CREATE',
          entityType: 'User',
          entityId: created.id,
          after: {
            username: created.username,
            fullName: created.fullName,
            email: created.email,
            role: created.role.name,
          },
          ip,
        });
        return created;
      });
      return { user: toView(user), temporaryPassword };
    } catch (err) {
      if (isUniqueViolation(err)) throw usernameTaken();
      throw err;
    }
  },

  async update(
    actorId: string,
    targetId: string,
    input: UpdateUserInput,
    ip: string | null,
  ): Promise<UserView> {
    const updated = await prisma.$transaction(async (tx) => {
      // Serialise with any other admin-changing transaction before reading state.
      await usersRepository.lockActiveAdmins(tx);
      const current = await usersRepository.findById(targetId, tx);
      if (!current) throw notFound('User not found');

      if (
        actorId === targetId &&
        ((input.role !== undefined && input.role !== current.role.name) ||
          (input.isActive !== undefined && input.isActive !== current.isActive))
      ) {
        throw lastAdmin('You cannot change your own role or active status');
      }

      const data: Prisma.UserUncheckedUpdateInput = {};
      if (input.fullName !== undefined) data.fullName = input.fullName;
      if (input.email !== undefined) data.email = input.email;
      if (input.isActive !== undefined) data.isActive = input.isActive;
      if (input.role !== undefined) {
        data.roleId = (await usersRepository.ensureRole(input.role, tx)).id;
      }

      const next = await usersRepository.update(targetId, data, tx);

      // Post-change invariant: at least one active ADMIN must remain (rolls back otherwise).
      if ((await usersRepository.countActiveAdmins(tx)) === 0) {
        throw lastAdmin('At least one active administrator must remain');
      }

      if (input.isActive === false && current.isActive) {
        await sessionService.revokeAllForUser(targetId, { tx });
      }

      const beforeView = toView(current);
      const afterView = toView(next);
      const before: Record<string, string | boolean | null> = {};
      const after: Record<string, string | boolean | null> = {};
      for (const key of TRACKED) {
        if (beforeView[key] !== afterView[key]) {
          before[key] = beforeView[key];
          after[key] = afterView[key];
        }
      }
      await auditService.record(tx, {
        userId: actorId,
        action: 'UPDATE',
        entityType: 'User',
        entityId: targetId,
        before,
        after,
        ip,
      });
      return next;
    });
    return toView(updated);
  },

  async resetPassword(
    actorId: string,
    targetId: string,
    ip: string | null,
  ): Promise<{ temporaryPassword: string }> {
    if (!(await usersRepository.findById(targetId))) throw notFound('User not found');
    const temporaryPassword = generateTemporaryPassword();
    const passwordHash = await hashPassword(temporaryPassword);
    await prisma.$transaction(async (tx) => {
      await usersRepository.update(
        targetId,
        { passwordHash, mustChangePassword: true, failedLoginCount: 0, lockedUntil: null },
        tx,
      );
      await sessionService.revokeAllForUser(targetId, { tx });
      await auditService.record(tx, {
        userId: actorId,
        action: 'UPDATE',
        entityType: 'User',
        entityId: targetId,
        after: { passwordReset: true, mustChangePassword: true },
        ip,
      });
    });
    return { temporaryPassword };
  },
};
