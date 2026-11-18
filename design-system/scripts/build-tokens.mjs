#!/usr/bin/env node
/**
 * Compiles design-system/hms/tokens.json (primitive -> semantic -> component, with
 * {path.to.token} references) into frontend/src/styles/tokens.css.
 * Same output format as the ui-ux-pro-max design-system generator, vendored here so the
 * build does not depend on a locally installed plugin.
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const tokens = JSON.parse(readFileSync(resolve(root, 'design-system/hms/tokens.json'), 'utf8'));

function resolveRef(value, seen = new Set()) {
  if (typeof value !== 'string' || !value.startsWith('{')) return value;
  const path = value.slice(1, -1);
  if (seen.has(path)) throw new Error(`Circular token reference: ${path}`);
  seen.add(path);
  const node = path.split('.').reduce((o, k) => o?.[k], tokens);
  if (!node || node.$value === undefined) throw new Error(`Unknown token reference: ${value}`);
  return resolveRef(node.$value, seen);
}

function flatten(obj, prefix, out = {}) {
  for (const [key, value] of Object.entries(obj ?? {})) {
    if (key.startsWith('$')) continue;
    const path = [...prefix, key];
    if (value && typeof value === 'object' && value.$value !== undefined) {
      out[`--${path.join('-')}`] = resolveRef(value.$value);
    } else if (value && typeof value === 'object') {
      flatten(value, path, out);
    }
  }
  return out;
}

const block = (vars) => Object.entries(vars).map(([k, v]) => `  ${k}: ${v};`).join('\n');

export function buildCss() {
  return `/* Design Tokens - Auto-generated */
/* Do not edit directly - modify tokens.json instead */

/* === PRIMITIVES === */
:root {
${block(flatten(tokens.primitive, ['primitive']))}
}

/* === SEMANTIC === */
:root {
${block(flatten(tokens.semantic, []))}
}

/* === COMPONENTS === */
:root {
${block(flatten(tokens.component, []))}
}
`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const target = resolve(root, 'frontend/src/styles/tokens.css');
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, buildCss());
  console.log(`Generated ${target}`);
}
