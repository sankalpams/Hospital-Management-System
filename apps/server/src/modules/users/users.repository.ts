import type { Prisma, RoleName } from '@prisma/client';
import { prisma } from '../../db.js';

type Db = Prisma.TransactionClient;

/** Safe projection: passwordHash and lockout internals are never selected. */
export const userSelect = {
  id: true,
  username: true,
  fullName: true,
  email: true,
  isActive: true,
  mustChangePassword: true,
  lastLoginAt: true,
  createdAt: true,
  updatedAt: true,
  role: { select: { name: true } },
} satisfies Prisma.UserSelect;

export type UserRow = Prisma.UserGetPayload<{ select: typeof userSelect }>;

export interface UserListFilter {
  search?: string;
  role?: RoleName;
  isActive?: boolean;
}

export const usersRepository = {
  async list(filter: UserListFilter, skip: number, take: number) {
    const where: Prisma.UserWhereInput = {
      isActive: filter.isActive,
      role: filter.role ? { name: filter.role } : undefined,
      OR: filter.search
        ? [
            { username: { contains: filter.search, mode: 'insensitive' } },
            { fullName: { contains: filter.search, mode: 'insensitive' } },
          ]
        : undefined,
    };
    const [items, total] = await prisma.$transaction([
      prisma.user.findMany({
        where,
        orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
        skip,
        take,
        select: userSelect,
      }),
      prisma.user.count({ where }),
    ]);
    return { items, total };
  },

  findById(id: string, db: Db = prisma) {
    return db.user.findUnique({ where: { id }, select: userSelect });
  },

  findByUsername(username: string, db: Db = prisma) {
    return db.user.findUnique({ where: { username }, select: { id: true } });
  },

  /** Roles are reference rows; make sure the row exists before linking a user to it. */
  ensureRole(name: RoleName, db: Db = prisma) {
    return db.role.upsert({ where: { name }, update: {}, create: { name, description: name } });
  },

  create(data: Prisma.UserUncheckedCreateInput, db: Db = prisma) {
    return db.user.create({ data, select: userSelect });
  },

  update(id: string, data: Prisma.UserUncheckedUpdateInput, db: Db = prisma) {
    return db.user.update({ where: { id }, data, select: userSelect });
  },

  /** Locks every active ADMIN row so concurrent admin-changing transactions serialise. */
  async lockActiveAdmins(db: Db): Promise<void> {
    await db.$queryRaw`
      SELECT u."id" FROM "users" u JOIN "roles" r ON r."id" = u."roleId"
      WHERE r."name" = 'ADMIN' AND u."isActive" = true FOR UPDATE OF u`;
  },

  countActiveAdmins(db: Db): Promise<number> {
    return db.user.count({ where: { isActive: true, role: { name: 'ADMIN' } } });
  },
};
