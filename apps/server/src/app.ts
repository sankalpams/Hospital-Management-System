import express, { type Express } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import cookieParser from 'cookie-parser';
import { config } from './config.js';
import { errorHandler } from './http/errorHandler.js';
import { notFound } from './http/errors.js';
import { authRouter } from './modules/auth/auth.routes.js';
import { auditRouter } from './modules/audit/audit.routes.js';
import { healthRouter } from './modules/health/health.routes.js';

export interface AppOptions {
  /** Max login attempts per window for the auth rate limiter; false disables it. Default 20. */
  loginRateLimit?: number | false;
}

export function createApp(options: AppOptions = {}): Express {
  const loginRateLimit = options.loginRateLimit ?? 20;
  const app = express();
  app.locals.loginRateLimit = loginRateLimit;

  app.use(helmet());
  app.use(
    cors({
      origin: config.clientOrigin,
      credentials: true,
      exposedHeaders: ['X-Session-Expires-At'],
    }),
  );
  app.use(express.json({ limit: '1mb' }));
  app.use(cookieParser());

  const api = express.Router();
  api.use('/health', healthRouter);
  api.use('/auth', authRouter(loginRateLimit));
  api.use('/audit-logs', auditRouter());
  app.use('/api/v1', api);

  app.use((_req, _res, next) => next(notFound('Route not found')));
  app.use(errorHandler);
  return app;
}
