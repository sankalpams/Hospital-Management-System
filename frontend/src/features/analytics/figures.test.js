import { describe, expect, it } from 'vitest';
import { formatBucket, formatShort, formatValue, trendGroups } from './figures';

describe('analytics figures', () => {
  it('formats each unit', () => {
    expect(formatValue(33.333, 'percent')).toBe('33.3%');
    expect(formatValue(1234.5, 'LKR')).toBe('LKR 1,234.50');
    expect(formatValue(15.64, 'minutes')).toBe('15.6 min');
    expect(formatValue(1, 'days')).toBe('1 day');
    expect(formatValue(19615, 'count')).toBe('19,615');
    expect(formatShort(116653842.35, 'LKR')).toMatch(/^LKR 116.7m$/i);
    expect(formatShort(48000, 'LKR')).toBe('LKR 48,000');
  });

  it('labels buckets by granularity', () => {
    expect(formatBucket('2030-06-03', 'day')).toBe('3 Jun');
    expect(formatBucket('2030-06-03', 'week')).toBe('w/c 3 Jun');
    expect(formatBucket('2030-06-01', 'month')).toBe('Jun 2030');
  });

  it('groups series that share a chart', () => {
    const g = trendGroups([{ key: 'a', group: 'flow' }, { key: 'b', group: 'flow' }, { key: 'c', group: 'other' }]);
    expect(g.map((x) => x.series.length)).toEqual([2, 1]);
  });
});
