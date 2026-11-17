import type { RequestHandler } from 'express';
import type { ZodTypeAny } from 'zod';

export function validate(schema: ZodTypeAny, source: 'body' | 'query' | 'params'): RequestHandler {
  return (req, _res, next) => {
    const result = schema.safeParse(req[source]);
    if (!result.success) {
      next(result.error);
      return;
    }
    // Define as an own property so this works even where req.query is a getter.
    Object.defineProperty(req, source, {
      value: result.data,
      writable: true,
      configurable: true,
      enumerable: true,
    });
    next();
  };
}
