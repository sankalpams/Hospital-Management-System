import { describe, expect, it } from 'vitest';
import { VERSION } from './index.js';

describe('shared smoke', () => {
  it('exports the package version', () => {
    expect(VERSION).toBe('0.1.0');
  });
});
