import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { server } from '../test/server';
import { api, ApiError, setApiHandlers } from './api';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  setApiHandlers({});
});
afterAll(() => server.close());

describe('api()', () => {
  it('turns an error body into an ApiError with the right code', async () => {
    server.use(
      http.get('*/api/v1/x', () =>
        HttpResponse.json(
          { error: { code: 'ACCOUNT_LOCKED', message: 'Locked', details: { a: 1 } } },
          { status: 423 },
        ),
      ),
    );
    const err = await api('/x').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 423, code: 'ACCOUNT_LOCKED', message: 'Locked', details: { a: 1 } });
  });

  it('carries PASSWORD_CHANGE_REQUIRED without breaking', async () => {
    server.use(
      http.get('*/api/v1/x', () =>
        HttpResponse.json({ error: { code: 'PASSWORD_CHANGE_REQUIRED', message: 'm' } }, { status: 403 }),
      ),
    );
    await expect(api('/x')).rejects.toMatchObject({ status: 403, code: 'PASSWORD_CHANGE_REQUIRED' });
  });

  it('calls onUnauthenticated on 401', async () => {
    const onUnauthenticated = vi.fn();
    setApiHandlers({ onUnauthenticated });
    server.use(
      http.get('*/api/v1/x', () =>
        HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'no' } }, { status: 401 }),
      ),
    );
    await expect(api('/x')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);
  });

  it('reports X-Session-Expires-At and parses json / 204', async () => {
    const onSessionExpiry = vi.fn();
    setApiHandlers({ onSessionExpiry });
    server.use(
      http.get('*/api/v1/ok', () =>
        HttpResponse.json({ v: 1 }, { headers: { 'X-Session-Expires-At': '2030-01-01T00:00:00.000Z' } }),
      ),
      http.post('*/api/v1/none', () => new HttpResponse(null, { status: 204 })),
    );
    await expect(api<{ v: number }>('/ok')).resolves.toEqual({ v: 1 });
    expect(onSessionExpiry).toHaveBeenCalledWith(new Date('2030-01-01T00:00:00.000Z'));
    await expect(api('/none', { method: 'POST' })).resolves.toBeUndefined();
  });
});

describe('api() password change required', () => {
  it('notifies the handler on 403 PASSWORD_CHANGE_REQUIRED', async () => {
    const onPasswordChangeRequired = vi.fn();
    setApiHandlers({ onPasswordChangeRequired });
    server.use(
      http.get('*/api/v1/y', () =>
        HttpResponse.json(
          { error: { code: 'PASSWORD_CHANGE_REQUIRED', message: 'change it' } },
          { status: 403 },
        ),
      ),
    );
    await api('/y').catch(() => undefined);
    expect(onPasswordChangeRequired).toHaveBeenCalledTimes(1);
  });
});
