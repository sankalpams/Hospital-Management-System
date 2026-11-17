import { describe, expect, it } from 'vitest';
import { changePasswordSchema, loginSchema, passwordSchema } from './auth.js';
import { createUserSchema } from './user.js';

describe('auth schemas', () => {
  it('enforces the password policy', () => {
    expect(passwordSchema.safeParse('abcdefgh').success).toBe(false); // no digit
    expect(passwordSchema.safeParse('abc1234').success).toBe(false); // 7 chars
    expect(passwordSchema.safeParse('Password1!').success).toBe(true);
  });

  it('rejects passwords longer than 128 characters', () => {
    expect(passwordSchema.safeParse('a1' + 'x'.repeat(127)).success).toBe(false);
    expect(passwordSchema.safeParse('a1' + 'x'.repeat(126)).success).toBe(true);
  });

  it('normalises the login username', () => {
    expect(loginSchema.parse({ username: '  Admin ', password: 'x' }).username).toBe('admin');
  });

  it('validates change-password and user creation', () => {
    expect(changePasswordSchema.safeParse({ currentPassword: 'x', newPassword: 'short' }).success).toBe(false);
    expect(changePasswordSchema.safeParse({ currentPassword: 'x', newPassword: 'Password1!' }).success).toBe(true);
    expect(createUserSchema.safeParse({ username: 'ab', fullName: 'A B', role: 'DOCTOR' }).success).toBe(false);
    expect(createUserSchema.safeParse({ username: 'Dr.Silva', fullName: 'A B', role: 'DOCTOR' }).success).toBe(true);
    expect(createUserSchema.safeParse({ username: 'drsilva', fullName: 'A B', role: 'BOSS' }).success).toBe(false);
  });
});
