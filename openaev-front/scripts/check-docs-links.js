// Builds the documentation and checks that every link of docs/links.json, which the front and the
// backend open by key, points to an existing page and anchor of the built site.
// Runs `docs/run.py check`, which sets up the pinned docs toolchain in its own virtual environment.
// Extra arguments go to the check (`yarn check-docs-links --force`).
//
// Python lookup, shared with the Maven docs-check goal and the docs serve scripts: the minimum is
// the version in docs/.python-version. PYTHON, when set, is used as is and fails by name when it is
// not usable. Otherwise the first usable one of `py -3` (Windows), `python3`, `python`, then
// `python3.N` from 3.20 down to the minimum. A candidate whose `--version` fails or prints nothing
// (the Microsoft Store stub on Windows) or that is too old is skipped.
// spawnSync without a shell runs the interpreter the same way on Linux, macOS and Windows.

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// run.py exit codes
const TOOLCHAIN_FAILED = 3;
const SETUP_FAILED = 4;

const docsDirectory = new URL('../../docs/', import.meta.url);
const runScript = fileURLToPath(new URL('run.py', docsDirectory));
const minimum = readFileSync(new URL('.python-version', docsDirectory), 'utf8').trim().split('.').slice(0, 2).map(Number);
const minimumLabel = minimum.join('.');

const fail = (message) => {
  // eslint-disable-next-line no-console
  console.error(message);
  process.exit(SETUP_FAILED);
};

// [major, minor, label] of a candidate, or null when it does not run or does not print a version
const versionOf = ([command, ...args]) => {
  const result = spawnSync(command, [...args, '--version'], { encoding: 'utf8' });
  if (result.error || result.status !== 0) {
    return null;
  }
  const match = /Python (\d+)\.(\d+)(\.\S+)?/.exec(`${result.stdout}${result.stderr}`);
  return match ? [Number(match[1]), Number(match[2]), `${match[1]}.${match[2]}${match[3] ?? ''}`] : null;
};

const isRecentEnough = ([major, minor]) => major > minimum[0] || (major === minimum[0] && minor >= minimum[1]);

const findPython = () => {
  const explicit = process.env.PYTHON;
  if (explicit) {
    const version = versionOf([explicit]);
    if (!version) {
      fail(`PYTHON is set to ${explicit}, which does not run or does not print its version. Point it to Python ${minimumLabel} or later, or unset it.`);
    }
    if (!isRecentEnough(version)) {
      fail(`PYTHON is set to ${explicit}, which is Python ${version[2]}: Python ${minimumLabel} or later is needed (docs/.python-version).`);
    }
    return {
      candidate: [explicit],
      version,
    };
  }
  const candidates = [
    ...(process.platform === 'win32' ? [['py', '-3']] : []),
    ['python3'],
    ['python'],
    ...Array.from({ length: 20 - minimum[1] + 1 }, (_, i) => [`python3.${20 - i}`]),
  ];
  for (const candidate of candidates) {
    const version = versionOf(candidate);
    if (version && isRecentEnough(version)) {
      return {
        candidate,
        version,
      };
    }
  }
  return fail(`Python ${minimumLabel} or later (docs/.python-version) is needed to check the docs links: https://www.python.org/downloads/\n`
    + 'Set the PYTHON environment variable to its path if it is installed but not found.');
};

const { candidate: [command, ...args], version } = findPython();
// eslint-disable-next-line no-console
console.log(`Checking docs links with ${[command, ...args].join(' ')} (${version[2]})`);

const result = spawnSync(command, [...args, runScript, 'check', ...process.argv.slice(2)], { stdio: 'inherit' });
if (result.error) {
  fail(`Could not run ${[command, ...args].join(' ')}: ${result.error.message}`);
}
process.exit(result.status ?? TOOLCHAIN_FAILED);
