import type { Response } from 'express';
import { config } from '../../config.js';

export const SESSION_COOKIE = 'hms_sid';

const base = () => ({
  httpOnly: true,
  sameSite: 'lax' as const,
  secure: config.nodeEnv === 'production',
  path: '/',
});

export const setSessionCookie = (res: Response, token: string) =>
  res.cookie(SESSION_COOKIE, token, base());

export const clearSessionCookie = (res: Response) => res.clearCookie(SESSION_COOKIE, base());
