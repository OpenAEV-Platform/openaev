import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

// The product's colour helpers return design-system tokens (`var(--…)`), which are CSS values:
// MUI's `alpha()` parses its argument in JavaScript and throws on them, taking the screen down
// behind an error boundary. Tints of such a colour go through `tint()` (`color-mix`) instead.
const TOKEN_PRODUCERS = /var\(--|computeStatusStyle\(|getStatusColor\(|criticalityColor\(|criticalityStyle\(|colorStyles\b/;
const JS_COLOUR_CALL = /\b(?:alpha|hexToRGB|darken|lighten|emphasize)\(\s*([A-Za-z_$][\w$]*)/g;

const SRC = path.resolve(__dirname, '../..');

const walk = (dir: string, files: string[] = []): string[] => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== '__tests__') walk(full, files);
    } else if (/\.(ts|tsx|jsx)$/.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
};

describe('design-system tokens and JavaScript colour maths', () => {
  it('never hands a token-valued colour to a JavaScript colour function', () => {
    const offenders: string[] = [];
    for (const file of walk(SRC)) {
      const source = fs.readFileSync(file, 'utf8');
      if (!TOKEN_PRODUCERS.test(source)) continue;
      for (const [, identifier] of source.matchAll(JS_COLOUR_CALL)) {
        const declaration = new RegExp(`(?:const|let|var)\\s+${identifier}\\b[^\\n]*`, 'g');
        for (const [line] of source.matchAll(declaration)) {
          if (TOKEN_PRODUCERS.test(line)) {
            offenders.push(`${path.relative(SRC, file)}: ${line.trim()}`);
          }
        }
      }
    }
    expect(offenders).toEqual([]);
  });
});
