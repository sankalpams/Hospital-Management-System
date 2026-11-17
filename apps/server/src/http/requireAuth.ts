import type { RequestHandler } from 'express';
import type { Role } from '@hms/shared';
import { AppError, unauthenticated } from './errors.js';
import { effectiveExpiry, sessionService } from '../modules/auth/session.service.js';
import { clearSessionCookie, SESSION_COOKIE } from '../modules/auth/cookie.js';

export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: Role;
  mustChangePassword: boolean;
}

declare module 'express-serve-static-core' {
  interface Request {
    user?: AuthUser;
    sessionToken?: string;
    sessionId?: string;
  }
}

/**
 * Requires a valid session. Users flagged mustChangePassword are refused (403) unless
 * the route opts in with allowPasswordChange (me, change-password, logout).
 */
export function requireAuth(opts: { allowPasswordChange?: boolean } = {}): RequestHandler {
  return async (req, res, next) => {
    try {
      const token: unknown = req.cookies?.[SESSION_COOKIE];
      if (typeof token !== 'string' || token === '') {
        throw unauthenticated();
      }
      const result = await sessionService.validateAndTouch(token);
      if (!result) {
        clearSessionCookie(res);
        throw unauthenticated();
      }
      const { session, user } = result;
      req.user = {
        id: user.id,
        username: user.username,
        fullName: user.fullName,
        role: user.role.name as Role,
        mustChangePassword: user.mustChangePassword,
      };
      req.sessionToken = token;
      req.sessionId = session.id;
      res.setHeader('X-Session-Expires-At', effectiveExpiry(session).toISOString());
      if (user.mustChangePassword && !opts.allowPasswordChange) {
        throw new AppError(403, 'PASSWORD_CHANGE_REQUIRED', 'You must change your password first');
      }
      next();
    } catch (err) {
      next(err);
    }
  };
}
