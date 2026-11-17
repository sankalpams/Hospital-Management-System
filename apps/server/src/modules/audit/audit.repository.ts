import type { AuditAction, Prisma } from '@prisma/client';
import { prisma } from '../../db.js';

export interface AuditFilter {
  userId?: string;
  action?: AuditAction;
  entityType?: string;
  entityId?: string;
  from?: Date;
  to?: Date;
  /** When true, `to` is an exclusive upper bound (date-only end day -> start of next day). */
  toExclusive?: boolean;
}

export const auditRepository = {
  async list(filter: AuditFilter, skip: number, take: number) {
    const where: Prisma.AuditLogWhereInput = {
      userId: filter.userId,
      action: filter.action,
      entityType: filter.entityType,
      entityId: filter.entityId,
      createdAt:
        filter.from || filter.to
          ? {
              gte: filter.from,
              ...(filter.toExclusive ? { lt: filter.to } : { lte: filter.to }),
            }
          : undefined,
    };
    const [items, total] = await prisma.$transaction([
      prisma.auditLog.findMany({
        where,
        orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
        skip,
        take,
        include: { user: { select: { username: true, fullName: true } } },
      }),
      prisma.auditLog.count({ where }),
    ]);
    return { items, total };
  },
};
