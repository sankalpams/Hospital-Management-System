import { Router } from 'express';
import { requireAuth } from '../../http/requireAuth.js';
import { requirePermission } from '../../http/requirePermission.js';
import { listAuditLogs } from './audit.controller.js';

export function auditRouter(): Router {
  const router = Router();
  // Read-only by design: audit logs are never updated or deleted through the API.
  router.get('/', requireAuth(), requirePermission('audit:view'), listAuditLogs);
  return router;
}
