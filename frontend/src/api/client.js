import axios from 'axios';

/**
 * Single Axios instance for the whole app.
 * - Same-origin (Vite proxy in dev, Apache in prod), so the session cookie is sent automatically.
 * - Axios reads the XSRF-TOKEN cookie and sends it as X-XSRF-TOKEN on state-changing requests.
 * - 401 / 403 PASSWORD_CHANGE_REQUIRED are broadcast as window events; AuthProvider reacts to them.
 */
export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 20000,
  headers: { Accept: 'application/json' },
});

export const AUTH_EVENT = 'hms:auth';

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.code;
    const isAuthCall = error.config?.url?.startsWith('/auth/');
    if (status === 401 && !isAuthCall) {
      window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: { type: 'unauthenticated', code } }));
    } else if (status === 403 && code === 'PASSWORD_CHANGE_REQUIRED') {
      window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: { type: 'password-change-required' } }));
    }
    return Promise.reject(error);
  },
);

/**
 * Normalises any request failure into { status, code, message, fieldErrors, requestId } so
 * pages can render errors uniformly. Never surfaces raw technical messages.
 */
export function toApiError(error) {
  const data = error?.response?.data;
  if (data && typeof data === 'object' && data.code) {
    return {
      status: data.status ?? error.response.status,
      code: data.code,
      message: data.message,
      fieldErrors: data.fieldErrors ?? [],
      requestId: data.requestId,
    };
  }
  if (error?.code === 'ECONNABORTED') {
    return { status: 0, code: 'TIMEOUT', message: 'The server took too long to respond. Please try again.', fieldErrors: [] };
  }
  if (!error?.response) {
    return { status: 0, code: 'NETWORK', message: 'Cannot reach the server. Check your connection and try again.', fieldErrors: [] };
  }
  return {
    status: error.response.status,
    code: 'UNKNOWN',
    message: 'Something went wrong. Please try again.',
    fieldErrors: [],
  };
}
