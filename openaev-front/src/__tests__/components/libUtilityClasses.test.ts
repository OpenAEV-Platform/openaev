import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * The product builds no Tailwind of its own, and a class the library does not ship
 * fails silently — no type error, no lint error, no failing render.
 */
const CSS = path.join(
  process.cwd(),
  'node_modules/@filigran/design-system/dist/index.css',
);
const SRC = path.join(process.cwd(), 'src');

/** Checked only when the library owns the FAMILY, so product selectors stay out. */
const familyOf = (cls: string) => (cls.includes('-') ? cls.slice(0, cls.indexOf('-') + 1) : null);

const walk = (dir: string, out: string[] = []): string[] => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (/\.(tsx|jsx)$/.test(entry.name) && !full.includes('__tests__')) out.push(full);
  }
  return out;
};

/** Every class the shipped stylesheet defines, escapes resolved. */
const shippedClasses = (): Set<string> => {
  const css = fs.readFileSync(CSS, 'utf8');
  const found = new Set<string>();
  for (const [, raw] of css.matchAll(/\.((?:[\w-]|\\.)+)(?=[\s,{:>~+)])/g)) {
    found.add(raw.replace(/\\(.)/g, '$1'));
  }
  return found;
};

/** Every literal class the sources write in a className, variants stripped. */
const writtenClasses = (): Map<string, string[]> => {
  const byClass = new Map<string, string[]>();
  for (const file of walk(SRC)) {
    const source = fs.readFileSync(file, 'utf8');
    for (const [, quoted, templated] of source.matchAll(/className=(?:"([^"]*)"|\{`([^`${}]*)`\})/g)) {
      for (const token of (quoted ?? templated ?? '').split(/\s+/)) {
        if (!token || /[${}()[\]]/.test(token)) continue;
        const base = token.split(':').pop() as string;
        if (!base || !familyOf(base)) continue;
        byClass.set(base, [...(byClass.get(base) ?? []), path.relative(process.cwd(), file)]);
      }
    }
  }
  return byClass;
};

describe('utility classes the library ships', () => {
  it('the reference stylesheet is the one the product imports', () => {
    expect(fs.existsSync(CSS)).toBe(true);
  });

  // Walks every source file, which sits close to the 5s default under load.
  it('every class written in a className exists in that stylesheet', () => {
    const shipped = shippedClasses();
    const families = new Set([...shipped].map(familyOf).filter(Boolean));
    const missing = [...writtenClasses().entries()]
      .filter(([cls]) => !shipped.has(cls) && families.has(familyOf(cls)))
      .map(([cls, files]) => `${cls} (${[...new Set(files)].slice(0, 3).join(', ')})`);
    expect(missing, 'these classes resolve to nothing at runtime').toEqual([]);
  }, 30_000);
});
