import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, Session } from '../types/api';

const SESSION_KEY = 'hms.session';
let refreshing: Promise<Session> | null = null;
const listeners = new Set<(session: Session | null) => void>();

export const sessionStore = {
  get: (): Session | null => { try { const raw = sessionStorage.getItem(SESSION_KEY); return raw ? JSON.parse(raw) as Session : null; } catch { return null; } },
  set: (session: Session) => { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); listeners.forEach((listener) => listener(session)); },
  clear: () => { sessionStorage.removeItem(SESSION_KEY); listeners.forEach((listener) => listener(null)); },
  subscribe: (listener: (session: Session | null) => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
};

export const api = axios.create({ baseURL: '/api/v1', headers: { 'Content-Type': 'application/json' } });
const anonymous = axios.create({ baseURL: '/api/v1', headers: { 'Content-Type': 'application/json' } });

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = sessionStore.get()?.accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use((response) => response, async (error: AxiosError<ApiError>) => {
  const request = error.config as InternalAxiosRequestConfig & { _retried?: boolean };
  const session = sessionStore.get();
  if (error.response?.status !== 401 || request?._retried || !session?.refreshToken || request.url?.includes('/auth/refresh')) return Promise.reject(error);
  request._retried = true;
  try {
    refreshing ??= anonymous.post<Session>('/auth/refresh', { refreshToken: session.refreshToken }).then((response) => response.data).finally(() => { refreshing = null; });
    const fresh = await refreshing;
    sessionStore.set(fresh);
    request.headers.Authorization = `Bearer ${fresh.accessToken}`;
    return api(request);
  } catch (refreshError) {
    sessionStore.clear();
    if (window.location.pathname !== '/login') window.location.assign('/login');
    return Promise.reject(refreshError);
  }
});

export const apiMessage = (error: unknown) => axios.isAxiosError<ApiError>(error) ? error.response?.data?.message ?? 'The request could not be completed.' : 'The request could not be completed.';
