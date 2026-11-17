import type { Prisma } from '@prisma/client';
import { prisma } from '../../db.js';

type Db = Prisma.TransactionClient;

export const sessionRepository = {
  create(data: Prisma.SessionUncheckedCreateInput, db: Db = prisma) {
    return db.session.create({ data });
  },
  findByTokenHash(tokenHash: string, db: Db = prisma) {
    return db.session.findUnique({
      where: { tokenHash },
      include: { user: { include: { role: true } } },
    });
  },
  touch(id: string, data: { lastSeenAt: Date; idleExpiresAt: Date }, db: Db = prisma) {
    return db.session.update({ where: { id }, data });
  },
  deleteById(id: string, db: Db = prisma) {
    return db.session.deleteMany({ where: { id } });
  },
  deleteByTokenHash(tokenHash: string, db: Db = prisma) {
    return db.session.deleteMany({ where: { tokenHash } });
  },
  deleteAllForUser(userId: string, exceptId: string | undefined, db: Db = prisma) {
    return db.session.deleteMany({
      where: { userId, ...(exceptId ? { id: { not: exceptId } } : {}) },
    });
  },
};
