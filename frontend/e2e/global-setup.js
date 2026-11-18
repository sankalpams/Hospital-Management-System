import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

/**
 * Provisions one e2e user per role through the real API (create or reset, then change the temporary
 * password), and saves each signed-in session as Playwright storage state in e2e/.auth (git-ignored).
 * Needs the dev admin password from the repo's .env (HMS_DEV_ADMIN_PASSWORD); it is never printed.
 */
export const ROLES = ['SUPER_ADMIN', 'HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'LAB_STAFF', 'PHARMACIST', 'ACCOUNTANT', 'HR_MANAGER'];
const here = path.dirname(fileURLToPath(import.meta.url));
export const AUTH_DIR = path.join(here, '.auth');

function env(key) {
  const text = readFileSync(path.join(here, '..', '..', '.env'), 'utf8');
  const line = text.split(/\r?\n/).find((l) => l.startsWith(`${key}=`));
  if (!line) throw new Error(`${key} is missing from .env`);
  return line.slice(key.length + 1).trim();
}

/** Minimal cookie-jar client for the API (session + XSRF double-submit). */
class Client {
  constructor(base) { this.base = base; this.cookies = {}; }

  async call(method, url, body) {
    if (!this.cookies['XSRF-TOKEN'] && method !== 'GET') await this.call('GET', '/api/v1/auth/csrf');
    const res = await fetch(this.base + url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        Cookie: Object.entries(this.cookies).map(([k, v]) => `${k}=${v}`).join('; '),
        ...(this.cookies['XSRF-TOKEN'] ? { 'X-XSRF-TOKEN': this.cookies['XSRF-TOKEN'] } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    for (const c of res.headers.getSetCookie()) {
      const [pair] = c.split(';');
      const i = pair.indexOf('=');
      const name = pair.slice(0, i);
      const value = pair.slice(i + 1);
      if (value) this.cookies[name] = value; else delete this.cookies[name];
    }
    const text = await res.text();
    const json = text && /^[[{]/.test(text) ? JSON.parse(text) : null;
    if (!res.ok) throw new Error(`${method} ${url} -> ${res.status} ${json?.code ?? ''}`);
    return json;
  }
}

export default async function globalSetup(config) {
  const base = config.projects[0].use.baseURL;
  mkdirSync(AUTH_DIR, { recursive: true });
  const admin = new Client(base);
  await admin.call('POST', '/api/v1/auth/login', { username: env('HMS_BOOTSTRAP_ADMIN_USERNAME'), password: env('HMS_DEV_ADMIN_PASSWORD') });

  const credentials = {};
  for (const role of ROLES) {
    const username = `e2e.${role.toLowerCase().replace('_', '-')}`;
    const found = (await admin.call('GET', `/api/v1/users?q=${username}&size=5`)).content.find((u) => u.username === username);
    const temporary = found
      ? (await admin.call('POST', `/api/v1/users/${found.id}/reset-password`, {})).temporaryPassword
      : (await admin.call('POST', '/api/v1/users', { username, fullName: `E2E ${role.replace('_', ' ').toLowerCase()}`, roles: [role] })).temporaryPassword;
    const password = `E2e-${randomBytes(9).toString('base64url')}7a`;
    const user = new Client(base);
    await user.call('POST', '/api/v1/auth/login', { username, password: temporary });
    await user.call('POST', '/api/v1/auth/change-password', { currentPassword: temporary, newPassword: password });
    const me = await user.call('GET', '/api/v1/auth/me');
    credentials[role] = { username, password, permissions: me.permissions };
    const { hostname } = new URL(base);
    writeFileSync(path.join(AUTH_DIR, `${role}.json`), JSON.stringify({
      cookies: Object.entries(user.cookies).map(([name, value]) => ({
        name, value, domain: hostname, path: '/', expires: -1, httpOnly: name === 'HMS_SESSION', secure: false, sameSite: 'Strict',
      })),
      origins: [],
    }));
  }
  // test-only credentials for the UI login test; git-ignored with the rest of e2e/.auth
  writeFileSync(path.join(AUTH_DIR, 'credentials.json'), JSON.stringify(credentials));
}
