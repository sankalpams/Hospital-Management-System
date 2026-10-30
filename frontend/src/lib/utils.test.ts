import { describe, expect, it } from 'vitest';
import { formatDateTime, formatLkr } from './utils';

describe('display formatters', () => {
  it('formats LKR with a currency marker', () => expect(formatLkr(1250)).toContain('LKR'));
  it('formats UTC values in a readable local format', () => expect(formatDateTime('2026-01-01T00:00:00Z')).not.toBe('—'));
});
