// Test/support helpers: parse theme tokens out of tokens.css and compute WCAG contrast.
export type ThemeTokens = Record<string, string>;

function parseDecls(body: string): ThemeTokens {
  const out: ThemeTokens = {};
  for (const m of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) out[m[1]!] = m[2]!.trim();
  return out;
}

function blockBody(css: string, selectorRe: RegExp): string {
  const m = selectorRe.exec(css);
  if (!m) return '';
  const start = css.indexOf('{', m.index);
  let depth = 0;
  for (let i = start; i < css.length; i++) {
    if (css[i] === '{') depth++;
    else if (css[i] === '}' && --depth === 0) return css.slice(start + 1, i);
  }
  return '';
}

export function parseThemeTokens(css: string): { light: ThemeTokens; dark: ThemeTokens } {
  const stripped = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const light = parseDecls(blockBody(stripped, /(^|\})\s*:root\s*\{/));
  const dark = { ...light, ...parseDecls(blockBody(stripped, /\[data-theme="dark"\]\s*\{/)) };
  return { light, dark };
}

function channel(v: number): number {
  const c = v / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminance(hex: string): number {
  const h = hex.trim().replace('#', '');
  const full = h.length === 3 ? [...h].map((c) => c + c).join('') : h;
  const n = parseInt(full.slice(0, 6), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}

export function contrastRatio(fg: string, bg: string): number {
  const [a, b] = [luminance(fg), luminance(bg)];
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}
