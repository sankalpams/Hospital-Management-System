import { describe, expect, it } from 'vitest';
import express from 'express';
import request from 'supertest';
import { z } from 'zod';
import { errorHandler } from './errorHandler.js';
import { AppError } from './errors.js';
import { validate } from './validate.js';
import { createApp } from '../app.js';

function build(setup: (app: express.Express) => void) {
  const app = express();
  app.use(express.json());
  setup(app);
  app.use(errorHandler);
  return app;
}

describe('errorHandler', () => {
  it('maps unknown errors to 500 INTERNAL without a stack trace', async () => {
    const app = build((a) =>
      a.get('/boom', () => {
        throw new Error('secret detail');
      }),
    );
    const res = await request(app).get('/boom');
    expect(res.status).toBe(500);
    expect(res.body.error.code).toBe('INTERNAL');
    expect(JSON.stringify(res.body)).not.toMatch(/secret detail|at .*\.ts|stack/);
  });

  it('maps AppError to its status and code', async () => {
    const app = build((a) =>
      a.get('/x', () => {
        throw new AppError(409, 'CONFLICT', 'dup', { a: 1 });
      }),
    );
    const res = await request(app).get('/x');
    expect(res.status).toBe(409);
    expect(res.body).toEqual({ error: { code: 'CONFLICT', message: 'dup', details: { a: 1 } } });
  });

  it('validate turns ZodError into 400 VALIDATION_ERROR and replaces the body', async () => {
    const schema = z.object({ n: z.coerce.number() });
    const app = build((a) =>
      a.post('/v', validate(schema, 'body'), (req, res) => {
        res.json(req.body);
      }),
    );
    const bad = await request(app).post('/v').send({ n: 'abc' });
    expect(bad.status).toBe(400);
    expect(bad.body.error.code).toBe('VALIDATION_ERROR');
    expect(bad.body.error.details).toBeDefined();
    const ok = await request(app).post('/v').send({ n: '5' });
    expect(ok.body).toEqual({ n: 5 });
  });

  it('malformed JSON returns 400 BAD_REQUEST via createApp', async () => {
    const res = await request(createApp())
      .post('/api/v1/anything')
      .set('Content-Type', 'application/json')
      .send('{"a":');
    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: { code: 'BAD_REQUEST', message: 'Malformed request body' } });
  });

  it('body over 1mb returns 413 PAYLOAD_TOO_LARGE via createApp', async () => {
    const res = await request(createApp())
      .post('/api/v1/anything')
      .set('Content-Type', 'application/json')
      .send(JSON.stringify({ blob: 'x'.repeat(1024 * 1024 + 10) }));
    expect(res.status).toBe(413);
    expect(res.body.error.code).toBe('PAYLOAD_TOO_LARGE');
  });
});
