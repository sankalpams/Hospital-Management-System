import type { Prisma } from '@prisma/client';
import { prisma } from '../../db.js';

type Db = Prisma.TransactionClient;

export const settingsRepository = {
  findAll(db: Db = prisma) {
    return db.setting.findMany();
  },
  upsert(key: string, value: Prisma.InputJsonValue, db: Db = prisma) {
    return db.setting.upsert({ where: { key }, update: { value }, create: { key, value } });
  },
};
