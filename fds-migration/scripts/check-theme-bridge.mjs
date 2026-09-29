#!/usr/bin/env node
/**
 * The custom theme reaches the library through CSS custom properties written by
 * `useFdsThemeScope`. Two things can silently break that path:
 *
 *  - the generated token snapshot drifting from the library's own `theme.css`,
 *    which would make the bridge compare a customer colour against a stale
 *    default and decide it is not an override;
 *  - a token the bridge writes being renamed upstream, which no type check sees
 *    because the names are strings.
 *
 * Both are checked here against the INSTALLED library, never against a copy.
 */
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const front = join(root, 'openaev-front');
const themeCss = join(
  front,
  'node_modules/@filigran/design-system/packages/filigran-design-system/dist/tokens/theme.css',
);
const metaFile = join(front, 'src/components/fds-tokens.generated.meta.json');
const tokensFile = join(front, 'src/components/fds-tokens.generated.ts');
const bridgeFile = join(front, 'src/utils/hooks/useFdsThemeScope.ts');

const fail = [];

const read = (path) => {
  try {
    return readFileSync(path, 'utf8');
  } catch {
    fail.push(`missing: ${path.replace(root, '.')}`);
    return null;
  }
};

const css = read(themeCss);
const meta = read(metaFile);
if (css && meta) {
  const installed = `sha256:${createHash('sha256').update(css).digest('hex')}`;
  const declared = JSON.parse(meta).themeCssHash;
  if (installed !== declared) {
    fail.push(
      `token snapshot is stale: theme.css is ${installed}, the snapshot declares ${declared}. `
      + 'Regenerate it from the design system repo.',
    );
  }
}

const tokens = read(tokensFile);
const bridge = read(bridgeFile);
if (tokens && bridge) {
  // Every `--token` the bridge names must still exist in the snapshot, in both modes.
  const named = [...new Set(bridge.match(/--[a-z0-9-]+/g) ?? [])]
    .filter(name => !name.startsWith('--tw-'));
  const layers = [0, 1, 2, 3];
  // A family is written through a template literal, so its name arrives here
  // without the layer index: `--bg-elevation-default-layer-`.
  const expand = name => (name.endsWith('-layer-')
    ? layers.map(n => `${name}${n}`)
    : [name]);
  const wanted = named.flatMap(expand);
  const missing = wanted.filter(name => !tokens.includes(`"${name}"`));
  if (missing.length) {
    fail.push(`tokens the bridge writes but the snapshot does not declare: ${missing.join(', ')}`);
  }
}

if (fail.length) {
  console.error('theme bridge check failed:');
  fail.forEach(line => console.error(`  - ${line}`));
  process.exit(1);
}
console.log('theme bridge check: OK');
