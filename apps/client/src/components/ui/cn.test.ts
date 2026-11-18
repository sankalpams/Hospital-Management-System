import { describe, expect, it } from 'vitest';
import { cn } from './cn';

describe('cn', () => {
  it('keeps the last font size', () => expect(cn('text-sm', 'text-4xl')).toBe('text-4xl'));
  it('keeps the last text colour', () => expect(cn('text-muted', 'text-primary')).toBe('text-primary'));
  it('keeps size and colour together', () => expect(cn('text-sm', 'text-muted')).toBe('text-sm text-muted'));
  it('keeps the last radius', () => expect(cn('rounded-card', 'rounded-pill')).toBe('rounded-pill'));
  it('keeps the last shadow', () => expect(cn('shadow-card', 'shadow-raised')).toBe('shadow-raised'));
  it('keeps the last background', () => expect(cn('bg-surface', 'bg-surface-raised')).toBe('bg-surface-raised'));
  it('keeps the last duration', () => expect(cn('duration-fast', 'duration-slow')).toBe('duration-slow'));
  it('keeps the last easing', () => expect(cn('ease-out-soft', 'ease-linear')).toBe('ease-linear'));
  it('does not merge duration with easing', () =>
    expect(cn('duration-fast', 'ease-out-soft')).toBe('duration-fast ease-out-soft'));
});
