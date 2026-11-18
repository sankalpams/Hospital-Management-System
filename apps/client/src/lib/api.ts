export const API_BASE = '/api/v1';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown;

  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export interface ApiHandlers {
  onUnauthenticated?: () => void;
  onSessionExpiry?: (expiresAt: Date) => void;
}

let handlers: ApiHandlers = {};

/** Registers global reactions (AuthProvider owns these). Replaces any previous registration. */
export function setApiHandlers(next: ApiHandlers): void {
  handlers = next;
}

export type ApiInit = Omit<RequestInit, 'body'> & { json?: unknown; body?: BodyInit | null };

export async function api<T = void>(path: string, init: ApiInit = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  const finalHeaders = new Headers(headers);
  let body = rest.body;
  if (json !== undefined) {
    finalHeaders.set('Content-Type', 'application/json');
    body = JSON.stringify(json);
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    body,
    headers: finalHeaders,
    credentials: 'include',
  });

  const expires = res.headers.get('X-Session-Expires-At');
  if (expires) {
    const d = new Date(expires);
    if (!Number.isNaN(d.getTime())) handlers.onSessionExpiry?.(d);
  }

  if (!res.ok) {
    let code = 'UNKNOWN';
    let message = res.statusText || 'Request failed';
    let details: unknown;
    try {
      const data = (await res.json()) as { error?: { code?: string; message?: string; details?: unknown } };
      if (data.error) {
        code = data.error.code ?? code;
        message = data.error.message ?? message;
        details = data.error.details;
      }
    } catch {
      /* non-JSON error body */
    }
    if (res.status === 401) handlers.onUnauthenticated?.();
    // TODO(Task 9): a 403 with code PASSWORD_CHANGE_REQUIRED should route the user to the change-password screen.
    throw new ApiError(res.status, code, message, details);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
