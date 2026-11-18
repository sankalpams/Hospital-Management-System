#!/usr/bin/env node
/**
 * WCAG 2.1 contrast gate for every foreground/background pair the design system allows
 * (design-system/hms/MASTER.md §2). Exits non-zero if any pair drops below its minimum,
 * so a token change that breaks accessibility fails `npm run check`.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const tokens = JSON.parse(readFileSync(resolve(root, 'design-system/hms/tokens.json'), 'utf8'));

function hex(path) {
  let node = path.split('.').reduce((o, k) => o?.[k], tokens);
  let value = node?.$value;
  while (typeof value === 'string' && value.startsWith('{')) {
    node = value.slice(1, -1).split('.').reduce((o, k) => o?.[k], tokens);
    value = node?.$value;
  }
  if (!/^#[0-9a-f]{6}$/i.test(value ?? '')) throw new Error(`Token ${path} is not a hex colour: ${value}`);
  return value;
}

function luminance(h) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255)
    .map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function ratio(a, b) {
  const [x, y] = [luminance(a), luminance(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

const s = (n) => `semantic.color.${n}`;
const TEXT = 4.5;
const UI = 3.0;
const pairs = [
  ['Body text on page background', s('foreground'), s('background'), TEXT],
  ['Muted text on card', s('foreground-muted'), s('surface'), TEXT],
  ['Subtle text on card', s('foreground-subtle'), s('surface'), TEXT],
  ['Subtle text on page background', s('foreground-subtle'), s('background'), TEXT],
  ['Subtle text on muted surface', s('foreground-subtle'), s('surface-muted'), TEXT],
  ['Primary button label', s('primary-foreground'), s('primary'), TEXT],
  ['Primary button hover label', s('primary-foreground'), s('primary-hover'), TEXT],
  ['Link on card', s('primary'), s('surface'), TEXT],
  ['Soft primary badge', s('primary-soft-foreground'), s('primary-soft'), TEXT],
  ['Sidebar text', s('sidebar-foreground'), s('sidebar'), TEXT],
  ['Sidebar active item', s('primary-foreground'), s('sidebar-active'), TEXT],
  ['Success badge', s('success'), s('success-soft'), TEXT],
  ['Warning badge', s('warning'), s('warning-soft'), TEXT],
  ['Danger badge', s('danger'), s('danger-soft'), TEXT],
  ['Info badge', s('info'), s('info-soft'), TEXT],
  ['Neutral badge', s('neutral'), s('neutral-soft'), TEXT],
  ['Special badge', s('special'), s('special-soft'), TEXT],
  ['Danger text on card', s('danger'), s('surface'), TEXT],
  ['Focus ring against card (UI)', s('ring'), s('surface'), UI],
];

let failures = 0;
for (const [label, fg, bg, min] of pairs) {
  const r = ratio(hex(fg), hex(bg));
  const ok = r >= min;
  if (!ok) failures += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${r.toFixed(2).padStart(5)} : 1  (min ${min})  ${label}`);
}
console.log(failures ? `\n${failures} contrast check(s) failed.` : `\nAll ${pairs.length} contrast checks passed.`);
process.exit(failures ? 1 : 0);
