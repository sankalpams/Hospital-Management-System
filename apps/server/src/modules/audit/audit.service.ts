import type { AuditAction, Prisma } from '@prisma/client';
import type { Paged } from '../../http/paging.js';
import { auditRepository, type AuditFilter } from './audit.repository.js';

export interface AuditEntry {
  userId?: string | null;
  action: AuditAction;
  entityType: string;
  entityId?: string | null;
  before?: Prisma.InputJsonValue;
  after?: Prisma.InputJsonValue;
  ip?: string | null;
}

export const auditService = {
  /** Must be called with the transaction client of the change being recorded. */
  async record(tx: Prisma.TransactionClient, e: AuditEntry): Promise<void> {
    await tx.auditLog.create({
      data: {
        userId: e.userId ?? null,
        action: e.action,
        entityType: e.entityType,
        entityId: e.entityId ?? null,
        before: e.before,
        after: e.after,
        ip: e.ip ?? null,
      },
    });
  },

  async list(filter: AuditFilter, paging: { page: number; pageSize: number; skip: number; take: number }) {
    const { items, total } = await auditRepository.list(filter, paging.skip, paging.take);
    const result: Paged<(typeof items)[number]> = {
      items,
      page: paging.page,
      pageSize: paging.pageSize,
      total,
    };
    return result;
  },
};
