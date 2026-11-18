// The ESLint plugin and the dependency-cruiser config are TypeScript files that Node runs by
// stripping their types. An older Node fails on them with an unclear ERR_UNKNOWN_FILE_EXTENSION,
// so check first that Node meets the `engines` range of package.json. Kept free of dependencies
// and recent syntax so that it runs on the Node it rejects.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const { engines } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

const parse = version => version.split('.').map(Number);

const isAtLeast = (version, minimum) => {
  const [current, required] = [parse(version), parse(minimum)];
  const index = current.findIndex((part, i) => part !== required[i]);
  return index === -1 || current[index] > required[index];
};

export const checkNodeVersion = (version = process.versions.node) => {
  const minimum = /^>=\s*(\d+\.\d+\.\d+)$/.exec(engines.node)?.[1];
  if (!minimum) {
    throw new Error(`check-node-version only understands a ">= x.y.z" engines.node range, got "${engines.node}".`);
  }
  if (!isAtLeast(version, minimum)) {
    throw new Error(`OpenAEV front tooling requires Node ${engines.node} (package.json engines), but this is Node ${version}.\n`
      + 'Switch to Node 24, for example with `fnm use 24` or `nvm use 24`, in your shell and in the Node interpreter setting of your IDE.');
  }
};

// `node scripts/check-node-version.js` guards the scripts that load TypeScript files
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    checkNodeVersion();
  } catch (error) {
    // eslint-disable-next-line no-console
    console.error(error.message);
    process.exit(1);
  }
}
