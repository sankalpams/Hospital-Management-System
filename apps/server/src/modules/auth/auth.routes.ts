import { Router } from 'express';
import { rateLimit } from 'express-rate-limit';
import { changePasswordSchema, loginSchema } from '@hms/shared';
import { AppError } from '../../http/errors.js';
import { requireAuth } from '../../http/requireAuth.js';
import { validate } from '../../http/validate.js';
import { changePassword, login, logout, me } from './auth.controller.js';

export function authRouter(loginRateLimit: number | false): Router {
  const router = Router();
  const handlers = [];
  if (loginRateLimit !== false) {
    handlers.push(
      rateLimit({
        windowMs: 60_000,
        limit: loginRateLimit,
        standardHeaders: true,
        legacyHeaders: false,
        handler: (_req, _res, next) =>
          next(new AppError(429, 'RATE_LIMITED', 'Too many login attempts. Try again later.')),
      }),
    );
  }
  router.post('/login', ...handlers, validate(loginSchema, 'body'), login);
  router.post('/logout', requireAuth({ allowPasswordChange: true }), logout);
  router.get('/me', requireAuth({ allowPasswordChange: true }), me);
  router.post(
    '/change-password',
    requireAuth({ allowPasswordChange: true }),
    validate(changePasswordSchema, 'body'),
    changePassword,
  );
  return router;
}
