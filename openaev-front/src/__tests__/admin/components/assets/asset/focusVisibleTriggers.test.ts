import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * The app's global rule `:focus { outline: 0 }` removes every outline, so an element
 * made focusable with `tabIndex={0}` is invisible to a keyboard user unless it carries
 * its own focus-visible ring.
 */
const DIR = path.join(process.cwd(), 'src/admin/components/assets/asset');

/** Each opening JSX tag that carries tabIndex={0}, attributes included. */
const focusableTags = (source: string): string[] => source.match(/<[A-Za-z][\w.]*\b[^<>]*tabIndex=\{0\}[^<>]*>/g) ?? [];

describe('asset screens: focusable wrappers', () => {
  const files = fs.readdirSync(DIR).filter(name => name.endsWith('.tsx'));

  it('finds the asset components', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(files)('%s gives every tabIndex={0} element a focus-visible ring', (file) => {
    const source = fs.readFileSync(path.join(DIR, file), 'utf8');
    const missing = focusableTags(source).filter(tag => !/focus-visible:ring/.test(tag));
    expect(missing).toEqual([]);
  });
});
