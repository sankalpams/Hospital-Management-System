import { describe, expect, it } from 'vitest';
import { formatDate, formatDateTime, formatMoney } from './format.js';

describe('formatters', () => {
  it('formats LKR money from cents', () => {
    expect(formatMoney(150000)).toBe('Rs. 1,500.00');
    expect(formatMoney(0)).toBe('Rs. 0.00');
    expect(formatMoney(123456789, 'LKR')).toBe('LKR 1,234,567.89');
  });

  it('formats dates in Asia/Colombo', () => {
    expect(formatDate('2026-11-16T20:00:00Z')).toBe('17/11/2026'); // UTC+5:30
    expect(formatDateTime('2026-11-16T20:00:00Z')).toBe('17/11/2026 01:30');
  });
});
