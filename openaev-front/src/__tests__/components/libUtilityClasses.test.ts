import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * The product builds no Tailwind of its own: every utility class it writes has to
 * exist in the stylesheet the library ships. A class that does not simply does
 * nothing, silently — no type error, no lint error, no failing render.
 */
const CSS = path.join(
  process.cwd(),
  'node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.css',
);
const SRC = path.join(process.cwd(), 'src');

// Utilities the library's own stylesheet is known not to carry, with what to write instead.
const NOT_SHIPPED: Record<string, string> = {
  'tracking-widest': 'set letterSpacing in style — the library ships no generic tracking scale',
  'tracking-wider': 'set letterSpacing in style — the library ships no generic tracking scale',
  'tracking-tight': 'set letterSpacing in style — the library ships no generic tracking scale',
  'line-clamp-1': 'write the -webkit-box clamp in style',
  'line-clamp-2': 'write the -webkit-box clamp in style',
  'line-clamp-3': 'write the -webkit-box clamp in style',
  'text-alert-error': 'use text-feedback-error-primary',
};

const walk = (dir: string, out: string[] = []): string[] => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (/\.(tsx|jsx)$/.test(entry.name) && !full.includes('__tests__')) out.push(full);
  }
  return out;
};

describe('utility classes the library ships', () => {
  it('the reference stylesheet is the one the product imports', () => {
    expect(fs.existsSync(CSS)).toBe(true);
  });

  it.each(Object.entries(NOT_SHIPPED))('no source writes %s', (cls, instead) => {
    const offenders = walk(SRC)
      .filter(file => new RegExp(`className=[^\\n]*\\b${cls}\\b`).test(fs.readFileSync(file, 'utf8')))
      .map(file => path.relative(process.cwd(), file));
    expect(offenders, `${cls} does nothing: ${instead}`).toEqual([]);
  });
});
