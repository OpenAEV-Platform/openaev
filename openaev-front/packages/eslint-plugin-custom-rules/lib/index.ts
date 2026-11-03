import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import pkg from '../package.json' with { type: 'json' };
import classesRule from './rules/classes-rule.ts';
import noMutableExports from './rules/no-mutable-exports.ts';

// ESLint's cache identifies a plugin by its name and version only, so a rule edited without a
// version bump kept stale cached results. Hashing the sources into the version invalidates them.
const hashSources = () => {
  const hash = createHash('sha256');
  readdirSync(import.meta.dirname, {
    recursive: true,
    encoding: 'utf8',
  })
    .filter(file => file.endsWith('.ts'))
    .sort()
    .forEach(file => hash.update(file).update(readFileSync(join(import.meta.dirname, file))));
  return hash.digest('hex').slice(0, 12);
};

const plugin = {
  meta: {
    name: pkg.name,
    version: `${pkg.version}+${hashSources()}`,
  },
  rules: {
    'classes-rule': classesRule,
    'no-mutable-exports': noMutableExports,
  },
};

// Assigned onto `plugin` so that the config refers to the very object it is exported as
export default Object.assign(plugin, {
  configs: {
    recommended: {
      name: 'custom-rules/recommended',
      plugins: { 'custom-rules': plugin },
      rules: {
        'custom-rules/classes-rule': 'error',
        'custom-rules/no-mutable-exports': 'error',
      },
    },
  },
});
