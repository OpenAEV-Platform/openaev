// Builds the documentation and checks that every link of docs/links.json, which the front and the
// backend open by key, points to an existing page and anchor of the built site.
// Runs `docs/run.py check`, which sets up the pinned docs toolchain in its own virtual environment.
// Extra arguments go to the check (`yarn check-docs-links --force`), after the `check` command, so
// options of run.py itself such as `--offline` cannot be passed this way: run
// `python3 docs/run.py --offline check` instead. Unlike the Maven docs-check, which only warns when
// it cannot run, this script is run on purpose: it exits with the code of run.py.
//
// This script only starts a Python: OPENAEV_DOCS_PYTHON when set, otherwise the first of `py -3`
// (Windows), `python3` and `python` that answers `--version`, which skips missing commands and the
// Microsoft Store stub on Windows. run.py checks the version and the venv module, and looks for a
// usable `python3.N` itself.
// spawnSync without a shell runs the interpreter the same way on Linux, macOS and Windows.

import { spawnSync } from 'node:child_process';
import { isAbsolute, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// run.py exit codes
const CHECK_CRASHED = 3;
const SETUP_FAILED = 4;

const EXPLICIT_VARIABLE = 'OPENAEV_DOCS_PYTHON';
const PROBE_TIMEOUT_MS = 10_000;

const runScript = fileURLToPath(new URL('../../docs/run.py', import.meta.url));

const fail = (message) => {
  // eslint-disable-next-line no-console
  console.error(message);
  process.exit(SETUP_FAILED);
};

const label = candidate => candidate.join(' ');

// Python variables of the developer's shell would change what the interpreter imports, for the
// probe and for run.py itself (a broken PYTHONHOME would otherwise exit 1, the docs-problems code)
const pythonEnv = Object.fromEntries(Object.entries(process.env)
  .filter(([name]) => !['PYTHONPATH', 'PYTHONHOME', 'PYTHONSAFEPATH'].includes(name)));

// Python 2 answers --version too, then fails on run.py with a syntax error (exit 1): skip it
const answers = ([command, ...args]) => {
  const probe = spawnSync(command, [...args, '--version'], {
    env: pythonEnv,
    encoding: 'utf8',
    timeout: PROBE_TIMEOUT_MS,
  });
  return probe.status === 0 && /^Python 3\./.test(`${probe.stdout ?? ''}${probe.stderr ?? ''}`.trim());
};

const findPython = () => {
  const explicit = process.env[EXPLICIT_VARIABLE];
  if (explicit) {
    // A path with a folder is relative to where yarn was started (INIT_CWD), not to openaev-front;
    // a bare command name stays as is and is looked up on the PATH.
    const hasFolder = /[\\/]/.test(explicit);
    return [hasFolder && !isAbsolute(explicit) ? resolve(process.env.INIT_CWD ?? process.cwd(), explicit) : explicit];
  }
  const candidates = [
    ...(process.platform === 'win32' ? [['py', '-3']] : []),
    ['python3'],
    ['python'],
  ];
  return candidates.find(answers) ?? fail(
    `No Python found (tried ${candidates.map(label).join(', ')}). Install Python, at least the version in `
    + 'docs/.python-version, from https://www.python.org/downloads/ (on Debian and Ubuntu, also install '
    + `python3-venv), or set ${EXPLICIT_VARIABLE} to its path.`,
  );
};

const candidate = findPython();
const [command, ...args] = candidate;
const explicit = Boolean(process.env[EXPLICIT_VARIABLE]);
// run.py reads OPENAEV_DOCS_PYTHON too: give it the absolute path
const env = {
  ...pythonEnv,
  ...(explicit && { [EXPLICIT_VARIABLE]: command }),
};

// Ctrl+C reaches the whole process group: let run.py stop and report it instead of dying first
process.on('SIGINT', () => {});
const result = spawnSync(command, [...args, runScript, 'check', ...process.argv.slice(2)], {
  env,
  stdio: 'inherit',
});
if (result.error) {
  const origin = explicit ? ` (${EXPLICIT_VARIABLE})` : '';
  fail(`Could not run ${label(candidate)}${origin}: ${result.error.message}`);
}
process.exit(result.status ?? CHECK_CRASHED);
