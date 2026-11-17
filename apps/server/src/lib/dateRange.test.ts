import { describe, expect, it } from 'vitest';
import {
  isDateOnly,
  isValidCalendarDate,
  startOfDayColombo,
  startOfNextDayColombo,
} from './dateRange.js';

describe('dateRange (Asia/Colombo, UTC+05:30)', () => {
  it('start of day is 00:00 Colombo = previous day 18:30Z', () => {
    expect(startOfDayColombo('2020-01-03').toISOString()).toBe('2020-01-02T18:30:00.000Z');
  });
  it('start of next day crosses month and year boundaries', () => {
    expect(startOfNextDayColombo('2020-01-03').toISOString()).toBe('2020-01-03T18:30:00.000Z');
    expect(startOfNextDayColombo('2020-12-31').toISOString()).toBe('2020-12-31T18:30:00.000Z');
    expect(startOfNextDayColombo('2020-02-29').toISOString()).toBe('2020-02-29T18:30:00.000Z');
  });
  it('detects date-only strings', () => {
    expect(isDateOnly('2020-01-03')).toBe(true);
    expect(isDateOnly('2020-01-03T00:00:00Z')).toBe(false);
  });

  it('validates calendar dates without rollover', () => {
    expect(isValidCalendarDate('2020-02-29')).toBe(true);
    expect(isValidCalendarDate('2020-02-30')).toBe(false);
    expect(isValidCalendarDate('2021-02-29')).toBe(false);
    expect(isValidCalendarDate('2020-13-01')).toBe(false);
    expect(isValidCalendarDate('2020-04-31')).toBe(false);
    expect(isValidCalendarDate('2020-1-01')).toBe(false);
  });
});
