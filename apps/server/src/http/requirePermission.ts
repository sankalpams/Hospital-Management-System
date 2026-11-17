import type { RequestHandler } from 'express';
import { can, type Permission } from '@hms/shared';
import { forbidden, unauthenticated } from './errors.js';
import { requireAuth } from './requireAuth.js';

/** Passes if the authenticated user's role holds ANY of the given permissions. Use after requireAuth. */
export function requirePermission(...perms: Permission[]): RequestHandler {
  return (req, _res, next) => {
    if (!req.user) {
      next(unauthenticated());
      return;
    }
    const role = req.user.role;
    if (perms.some((p) => can(role, p))) {
      next();
      return;
    }
    next(forbidden());
  };
}

export const requireAnyAuth = requireAuth;
