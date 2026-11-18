import { describe, expect, it } from 'vitest';
import { safeAppPath } from '../lib/forms';

describe('safeAppPath', () => {
  it('keeps in-app paths and refuses anything that could leave the site', () => {
    expect(safeAppPath('/patients/12?tab=lab')).toBe('/patients/12?tab=lab');
    expect(safeAppPath('//evil.example')).toBe('/');
    expect(safeAppPath('/\\evil.example')).toBe('/');
    expect(safeAppPath('https://evil.example')).toBe('/');
    expect(safeAppPath(undefined)).toBe('/');
    expect(safeAppPath('/x\\y', '/home')).toBe('/home');
  });
});
