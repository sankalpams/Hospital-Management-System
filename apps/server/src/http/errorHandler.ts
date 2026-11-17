import type { ErrorRequestHandler } from 'express';
import { ZodError } from 'zod';
import { logger } from '../logger.js';
import { AppError } from './errors.js';

export const errorHandler: ErrorRequestHandler = (err, _req, res, next) => {
  if (res.headersSent) {
    next(err);
    return;
  }
  if (err instanceof ZodError) {
    res.status(400).json({
      error: { code: 'VALIDATION_ERROR', message: 'Validation failed', details: err.flatten() },
    });
    return;
  }
  if (err instanceof AppError) {
    const error: { code: string; message: string; details?: unknown } = {
      code: err.code,
      message: err.message,
    };
    if (err.details !== undefined) error.details = err.details;
    res.status(err.status).json({ error });
    return;
  }
  const e = err as { status?: unknown; statusCode?: unknown; type?: unknown };
  const status = typeof e.status === 'number' ? e.status : e.statusCode;
  if (typeof e.type === 'string' && e.type.startsWith('entity.') && typeof status === 'number') {
    if (status === 413) {
      res
        .status(413)
        .json({ error: { code: 'PAYLOAD_TOO_LARGE', message: 'Request body too large' } });
      return;
    }
    if (status >= 400 && status < 500) {
      res.status(400).json({ error: { code: 'BAD_REQUEST', message: 'Malformed request body' } });
      return;
    }
  }
  logger.error({ err }, 'unhandled error');
  res.status(500).json({ error: { code: 'INTERNAL', message: 'Internal server error' } });
};
