import type { RequestHandler } from 'express';
import type { ChangePasswordInput, LoginInput } from '@hms/shared';
import { unauthenticated } from '../../http/errors.js';
import { authService } from './auth.service.js';
import { clearSessionCookie, setSessionCookie } from './cookie.js';

export const login: RequestHandler = async (req, res, next) => {
  try {
    const { username, password } = req.body as LoginInput;
    const { token, user } = await authService.login(username, password, {
      ip: req.ip ?? null,
      userAgent: req.get('user-agent') ?? null,
    });
    setSessionCookie(res, token);
    res.json({ user });
  } catch (err) {
    next(err);
  }
};

export const logout: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user || !req.sessionToken || !req.sessionId) throw unauthenticated();
    await authService.logout(req.user.id, req.sessionToken, req.sessionId, req.ip ?? null);
    clearSessionCookie(res);
    res.status(204).end();
  } catch (err) {
    next(err);
  }
};

export const me: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user || !req.sessionToken) throw unauthenticated();
    const header = res.getHeader('X-Session-Expires-At');
    res.json({ user: req.user, expiresAt: typeof header === 'string' ? header : null });
  } catch (err) {
    next(err);
  }
};

export const changePassword: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user || !req.sessionId) throw unauthenticated();
    const { currentPassword, newPassword } = req.body as ChangePasswordInput;
    await authService.changePassword(
      req.user.id,
      req.sessionId,
      currentPassword,
      newPassword,
      req.ip ?? null,
    );
    res.status(204).end();
  } catch (err) {
    next(err);
  }
};
