import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

// The product's colour helpers return design-system tokens (`var(--…)`), which are CSS values:
// MUI's `alpha()` parses its argument in JavaScript and throws on them, taking the screen down
// behind an error boundary. Tints of such a colour go through `tint()` (`color-mix`) instead.
const TOKEN_PRODUCERS = /var\(--|computeStatusStyle\(|getStatusColor\(|criticalityColor\(|criticalityStyle\(|colorStyles\b/;
const JS_COLOUR_NAMES = ['alpha', 'hexToRGB', 'darken', 'lighten', 'emphasize'];
const JS_COLOUR_CALL = new RegExp(`\\b(?:${JS_COLOUR_NAMES.join('|')})\\(\\s*([A-Za-z_$][\\w$]*)`, 'g');

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

/**
 * The whole declaration, not just its first line: a band reads its colour several lines
 * below `const band = (() => {`, which is exactly the shape that took the home dashboard
 * down. Walks from the declaration to the `;` that closes it, tracking nesting.
 */
const readStatement = (source: string, from: number): string => {
  let depth = 0;
  for (let i = from; i < source.length; i += 1) {
    const c = source[i];
    if (c === '(' || c === '{' || c === '[') depth += 1;
    else if (c === ')' || c === '}' || c === ']') depth -= 1;
    else if (c === ';' && depth <= 0) return source.slice(from, i);
  }
  return source.slice(from);
};

/**
 * Every declaration that can give this identifier its value, one hop deep: its own
 * `const x = …`, and the object it was destructured from (`const { color } = band`),
 * because a band builds its colour several lines inside `const band = (() => {`.
 * Not resolved: an imported constant, and a member expression used directly in the call.
 */
const declarationsOf = (source: string, identifier: string): string[] => {
  const own = [...source.matchAll(new RegExp(`(?:const|let|var)\\s+${identifier}\\b`, 'g'))]
    .map(match => readStatement(source, match.index ?? 0));
  const destructured = [...source.matchAll(new RegExp(`(?:const|let|var)\\s*\\{[^}]*\\b${identifier}\\b[^}]*\\}\\s*=\\s*([A-Za-z_$][\\w$]*)`, 'g'))]
    .flatMap(match => [...source.matchAll(new RegExp(`(?:const|let|var)\\s+${match[1]}\\b`, 'g'))]
      .map(inner => readStatement(source, inner.index ?? 0)));
  return [...own, ...destructured];
};

describe('design-system tokens and JavaScript colour maths', () => {
  it('never hands a token-valued colour to a JavaScript colour function', () => {
    const offenders: string[] = [];
    for (const file of walk(SRC)) {
      const source = fs.readFileSync(file, 'utf8');
      // Two cheap rejections before any regex work: the file has to hold both halves of
      // the defect. They drop ~9 files out of 10 for the cost of a substring search, which
      // keeps this scan from stealing time from the timing-sensitive tests beside it.
      if (!JS_COLOUR_NAMES.some(name => source.includes(`${name}(`))) continue;
      if (!TOKEN_PRODUCERS.test(source)) continue;
      const identifiers = new Set([...source.matchAll(JS_COLOUR_CALL)].map(match => match[1]));
      for (const identifier of identifiers) {
        for (const statement of declarationsOf(source, identifier)) {
          if (TOKEN_PRODUCERS.test(statement)) {
            offenders.push(`${path.relative(SRC, file)}: ${statement.split('\n')[0].trim()}`);
          }
        }
      }
    }
    expect(offenders).toEqual([]);
  }, 30_000);
});
