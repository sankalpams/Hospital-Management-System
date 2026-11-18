import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { contrastRatio, parseThemeTokens } from './contrast';

const css = readFileSync(resolve(__dirname, 'tokens.css'), 'utf8');
const { light, dark } = parseThemeTokens(css);

const TEXT_PAIRS: [string, string][] = [
  ['text', 'bg'], ['text', 'surface'], ['text', 'surface-raised'], ['text', 'surface-sunken'],
  ['muted', 'bg'], ['muted', 'surface'], ['muted', 'surface-sunken'],
  ['on-primary', 'primary'], ['on-primary', 'primary-hover'],
  ['primary', 'surface'], ['primary', 'bg'], ['primary-hover', 'primary-subtle'],
  ['success', 'success-subtle'], ['warning', 'warning-subtle'], ['danger', 'danger-subtle'], ['info', 'info-subtle'],
  ['success', 'surface'], ['warning', 'surface'], ['danger', 'surface'], ['info', 'surface'],
];
const UI_PAIRS: [string, string][] = [
  ['border-strong', 'surface'], ['primary', 'surface'],
  ...[1, 2, 3, 4, 5, 6].map((n): [string, string] => [`chart-${n}`, 'surface']),
];

describe('contrastRatio', () => {
  it('black on white is 21', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5);
  });
});

describe('parseThemeTokens', () => {
  it('dark overrides surface', () => {
    expect(dark['--color-surface']).toBeDefined();
    expect(dark['--color-surface']).not.toBe(light['--color-surface']);
  });
});

describe.each([['light', light], ['dark', dark]] as const)('%s theme contrast', (_name, t) => {
  it.each(TEXT_PAIRS)('text %s on %s >= 4.5', (fg, bg) => {
    expect(contrastRatio(t[`--color-${fg}`]!, t[`--color-${bg}`]!)).toBeGreaterThanOrEqual(4.5);
  });
  it.each(UI_PAIRS)('ui %s on %s >= 3', (fg, bg) => {
    expect(contrastRatio(t[`--color-${fg}`]!, t[`--color-${bg}`]!)).toBeGreaterThanOrEqual(3);
  });
});
