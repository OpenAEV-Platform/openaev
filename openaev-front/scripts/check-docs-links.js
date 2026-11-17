// Builds the documentation and checks that every link of docs/links.json, which the front and the
// backend open by key, points to an existing page and anchor of the built site.
// Runs `docs/run.py check`, which sets up the pinned docs toolchain in its own virtual environment.
// Extra arguments go to the check (`yarn check-docs-links --force`). Unlike the Maven docs-check,
// which only warns when Python is missing, this script is run on purpose and fails.
//
// Python lookup, shared with the Maven docs-check goal and the docs serve scripts: the minimum is
// the version in docs/.python-version, and a Python is usable when it can import venv and ensurepip.
// OPENAEV_DOCS_PYTHON, when set, is the only one tried and fails by name when it is not usable.
// Otherwise the first usable one of `py -3` (Windows), `python3`, `python`, then `python3.N` from
// 3.20 down to the minimum: one that does not run (the Microsoft Store stub on Windows), lacks venv
// (Debian and Ubuntu package it apart) or is too old is skipped.
// spawnSync without a shell runs the interpreter the same way on Linux, macOS and Windows.

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// run.py exit codes
const TOOLCHAIN_FAILED = 3;
const SETUP_FAILED = 4;

const EXPLICIT_VARIABLE = 'OPENAEV_DOCS_PYTHON';
const PROBE = 'import sys, venv, ensurepip; print("%d.%d" % sys.version_info[:2])';

const docsDirectory = new URL('../../docs/', import.meta.url);
const runScript = fileURLToPath(new URL('run.py', docsDirectory));
const minimum = readFileSync(new URL('.python-version', docsDirectory), 'utf8').trim().split('.').slice(0, 2).map(Number);
const minimumLabel = minimum.join('.');

const fail = (message) => {
  // eslint-disable-next-line no-console
  console.error(message);
  process.exit(SETUP_FAILED);
};

const label = candidate => candidate.join(' ');

// { version } when the candidate is usable, { problem } otherwise
const probe = ([command, ...args]) => {
  const result = spawnSync(command, [...args, '-c', PROBE], { encoding: 'utf8' });
  if (result.error) {
    return { problem: result.error.code === 'ENOENT' ? 'not found' : result.error.message };
  }
  const version = /^(\d+)\.(\d+)$/m.exec(result.stdout ?? '');
  if (result.status !== 0 || !version) {
    return { problem: /No module named/.test(result.stderr ?? '') ? 'no venv module' : 'does not run' };
  }
  const [major, minor] = [Number(version[1]), Number(version[2])];
  if (major < minimum[0] || (major === minimum[0] && minor < minimum[1])) {
    return { problem: `Python ${major}.${minor}, too old` };
  }
  return { version: `${major}.${minor}` };
};

const findPython = () => {
  const explicit = process.env[EXPLICIT_VARIABLE];
  if (explicit) {
    const { version, problem } = probe([explicit]);
    if (problem) {
      fail(`${EXPLICIT_VARIABLE} is set to ${explicit}, which is not usable (${problem}). Point it to Python ${minimumLabel} `
        + 'or later with its venv module, or unset it.');
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
  const skipped = [];
  for (const candidate of candidates) {
    const { version, problem } = probe(candidate);
    if (version) {
      return {
        candidate,
        version,
      };
    }
    if (problem !== 'not found') {
      skipped.push(`${label(candidate)}: ${problem}`);
    }
  }
  return fail(`Python ${minimumLabel} or later (docs/.python-version) with its venv module is needed to check the docs links: `
    + 'https://www.python.org/downloads/ (on Debian and Ubuntu, also install python3-venv, or python3.N-venv for a python3.N).\n'
    + (skipped.length ? `Skipped: ${skipped.join('; ')}.\n` : '')
    + `Set ${EXPLICIT_VARIABLE} to its path if it is installed but not found.`);
};

const { candidate, version } = findPython();
// eslint-disable-next-line no-console
console.log(`Checking docs links with ${label(candidate)} (${version})`);

const [command, ...args] = candidate;
const result = spawnSync(command, [...args, runScript, 'check', ...process.argv.slice(2)], { stdio: 'inherit' });
if (result.error) {
  fail(`Could not run ${label(candidate)}: ${result.error.message}`);
}
process.exit(result.status ?? TOOLCHAIN_FAILED);
