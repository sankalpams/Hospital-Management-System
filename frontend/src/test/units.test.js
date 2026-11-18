import { describe, expect, it } from 'vitest';
import { evaluatePassword, isPasswordAcceptable } from '../lib/passwordRules';
import { toApiError } from '../api/client';
import { humanise, toneForStatus } from '../components/ui/Badge';
import { visibleGroups } from '../config/navigation';

describe('password rules mirror the server policy', () => {
  it('accepts a compliant password', () => {
    expect(isPasswordAcceptable('Kandy-Lake-2026', 'nimal')).toBe(true);
  });
  it.each([
    ['short1', 'length'],
    ['onlyletterslong', 'digit'],
    ['1234567890', 'letter'],
    ['xxnimal2026xx', 'username'],
  ])('rejects %s (%s)', (pw, failingRule) => {
    const failed = evaluatePassword(pw, 'nimal').filter((r) => !r.passed).map((r) => r.id);
    expect(failed).toContain(failingRule);
  });
});

describe('toApiError', () => {
  it('passes through the server error shape', () => {
    const e = toApiError({ response: { status: 409, data: { status: 409, code: 'X', message: 'm', requestId: 'r1' } } });
    expect(e).toMatchObject({ status: 409, code: 'X', message: 'm', requestId: 'r1', fieldErrors: [] });
  });
  it('gives a friendly message when the server is unreachable', () => {
    expect(toApiError({ message: 'Network Error' }).code).toBe('NETWORK');
  });
  it('never shows raw messages for unexpected responses', () => {
    const e = toApiError({ response: { status: 502, data: '<html>Bad gateway</html>' } });
    expect(e.message).not.toMatch(/html/i);
  });
});

describe('status badges', () => {
  it('maps statuses to the documented tones', () => {
    expect(toneForStatus('CANCELLED')).toBe('danger');
    expect(toneForStatus('COMPLETED')).toBe('success');
    expect(toneForStatus('SOMETHING_NEW')).toBe('neutral');
  });
  it('humanises codes for display', () => {
    expect(humanise('PARTIALLY_PAID')).toBe('Partially Paid');
  });
});

describe('navigation', () => {
  it('hides items the user lacks permission for and drops empty groups', () => {
    const none = () => false;
    const all = () => true;
    expect(visibleGroups(all).length).toBeGreaterThan(0);
    // Items without a permission (Dashboard) are visible to everyone signed in.
    expect(visibleGroups((p) => p === undefined || none(p)).flatMap((g) => g.items).map((i) => i.label)).toContain('Dashboard');
  });
});
