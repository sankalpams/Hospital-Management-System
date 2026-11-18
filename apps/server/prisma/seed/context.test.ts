import { describe, expect, it, vi } from 'vitest';
import { assertSeedAllowed, parseScale } from './context.js';

describe('parseScale', () => {
  it('defaults to large when undefined', () => {
    expect(parseScale(undefined)).toBe('large');
  });
  it('accepts small and large case-insensitively', () => {
    expect(parseScale('small')).toBe('small');
    expect(parseScale('SMALL')).toBe('small');
    expect(parseScale('Large')).toBe('large');
    expect(parseScale('LARGE')).toBe('large');
  });
  it('throws on anything else', () => {
    expect(() => parseScale('smal')).toThrow(/SEED_SCALE/);
    expect(() => parseScale('')).toThrow(/SEED_SCALE/);
    expect(() => parseScale('medium')).toThrow(/SEED_SCALE/);
  });
});

describe('assertSeedAllowed', () => {
  it('allows non-production environments', () => {
    expect(() => assertSeedAllowed({ NODE_ENV: 'development' })).not.toThrow();
    expect(() => assertSeedAllowed({})).not.toThrow();
  });
  it('refuses in production without SEED_ALLOW_PRODUCTION=1', () => {
    expect(() => assertSeedAllowed({ NODE_ENV: 'production' })).toThrow(/SEED_ALLOW_PRODUCTION/);
    expect(() => assertSeedAllowed({ NODE_ENV: 'production', SEED_ALLOW_PRODUCTION: '0' })).toThrow();
  });
  it('allows production with SEED_ALLOW_PRODUCTION=1 and warns loudly', () => {
    const warn = vi.fn();
    expect(() =>
      assertSeedAllowed({ NODE_ENV: 'production', SEED_ALLOW_PRODUCTION: '1' }, warn),
    ).not.toThrow();
    expect(warn).toHaveBeenCalledOnce();
    expect(String(warn.mock.calls[0]?.[0])).toMatch(/WARNING/);
  });
});
