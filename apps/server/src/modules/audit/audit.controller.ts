import type { RequestHandler } from 'express';
import { z } from 'zod';
import {
  isDateOnly,
  isValidCalendarDate,
  startOfDayColombo,
  startOfNextDayColombo,
} from '../../lib/dateRange.js';
import { parsePaging } from '../../http/paging.js';
import { auditService } from './audit.service.js';

const ACTIONS = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'LOGIN',
  'LOGIN_FAILED',
  'LOGOUT',
  'VIEW_EMR',
  'EXPORT',
] as const;

type Bound = { at: Date; exclusive: boolean };

function dateBound(kind: 'from' | 'to') {
  return z.string().transform((v, ctx): Bound => {
    const dateOnly = isDateOnly(v);
    // Reject rolled-over days (2020-02-30) in both date-only and full timestamps.
    const datePart = v.slice(0, 10);
    const calendarOk = !/^\d{4}-\d{2}-\d{2}/.test(v) || isValidCalendarDate(datePart);
    const at = !calendarOk
      ? new Date(NaN)
      : dateOnly
        ? kind === 'from'
          ? startOfDayColombo(v)
          : startOfNextDayColombo(v)
        : new Date(v);
    if (Number.isNaN(at.getTime())) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Invalid date' });
      return z.NEVER;
    }
    return { at, exclusive: dateOnly && kind === 'to' };
  });
}

export const auditQuerySchema = z.object({
  userId: z.string().min(1).optional(),
  action: z.enum(ACTIONS).optional(),
  entityType: z.string().min(1).optional(),
  entityId: z.string().min(1).optional(),
  // Date-only values are whole Colombo days; full timestamps are used as exact instants.
  from: z.string().min(1).pipe(dateBound('from')).optional(),
  to: z.string().min(1).pipe(dateBound('to')).optional(),
});

export const listAuditLogs: RequestHandler = async (req, res, next) => {
  try {
    const q = auditQuerySchema.parse(req.query);
    const filter = {
      userId: q.userId,
      action: q.action,
      entityType: q.entityType,
      entityId: q.entityId,
      from: q.from?.at,
      to: q.to?.at,
      toExclusive: q.to?.exclusive ?? false,
    };
    const paging = parsePaging(req.query);
    res.json(await auditService.list(filter, paging));
  } catch (err) {
    next(err);
  }
};
