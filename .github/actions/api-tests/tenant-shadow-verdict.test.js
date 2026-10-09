// The tenant shadow shards are the rehearsal for retiring the activation list, and for weeks they
// reported success whether or not the suite passed. The verdict logic that replaced that silence
// lives in a shell step inside action.yml, where nothing else would exercise it. These tests pull
// the step out of the action and run it against fabricated Surefire reports.

const { test } = require('node:test');
const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const ACTION = path.join(__dirname, 'action.yml');

/**
 * The `run:` script of a named step, dedented. Hand-rolled because this test must not pull in a
 * YAML dependency for a repository that installs none for its action tests.
 */
function stepScript(stepName) {
  const lines = fs.readFileSync(ACTION, 'utf8').split('\n');
  const start = lines.findIndex((line) => line.trim() === `- name: ${stepName}`);
  assert.ok(start >= 0, `step not found in action.yml: ${stepName}`);
  const runAt = lines.findIndex((line, i) => i > start && line.trim() === 'run: |');
  assert.ok(runAt > start, `step has no literal run block: ${stepName}`);
  const indent = lines[runAt].search(/\S/) + 2;
  const body = [];
  for (let i = runAt + 1; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.trim() !== '' && line.search(/\S/) < indent) break;
    body.push(line.slice(indent));
  }
  return body.join('\n');
}

function runSummary({ reports, runOutcome = 'success', mode = 'all', shard = '3-shadow-all' }) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'shadow-verdict-'));
  const reportDir = path.join(dir, 'openaev-api/target/surefire-reports');
  fs.mkdirSync(reportDir, { recursive: true });
  for (const [name, content] of Object.entries(reports)) {
    fs.writeFileSync(path.join(reportDir, name), content);
  }
  const summary = path.join(dir, 'summary.md');
  fs.writeFileSync(summary, '');
  const script = path.join(dir, 'step.sh');
  fs.writeFileSync(script, stepScript('Summarise the multi-tenancy shadow run'));

  const stdout = execFileSync('bash', ['-eo', 'pipefail', script], {
    cwd: dir,
    encoding: 'utf8',
    env: {
      ...process.env,
      GITHUB_STEP_SUMMARY: summary,
      MODE: mode,
      SHARD: shard,
      RUN_OUTCOME: runOutcome,
    },
  });

  const verdictPath = path.join(dir, 'openaev-api/target/tenant-shadow-verdict.tsv');
  const verdict = fs.existsSync(verdictPath)
    ? fs.readFileSync(verdictPath, 'utf8').trimEnd().split('\t')
    : null;
  return { stdout, verdict, summary: fs.readFileSync(summary, 'utf8') };
}

const green = 'Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s\n';
const red = 'Tests run: 10, Failures: 2, Errors: 1, Skipped: 0, Time elapsed: 1 s\n';

test('a clean shard reports PASS with its counts', () => {
  const { verdict, summary } = runSummary({ reports: { 'A.txt': green, 'B.txt': green } });
  assert.deepStrictEqual(verdict.slice(0, 7), ['all', '3-shadow-all', 'PASS', '20', '0', '0', '0']);
  assert.match(summary, /### Verdict: PASS/);
});

test('a red shard reports FAIL and the number of failing tests reaches the summary', () => {
  const { verdict, summary, stdout } = runSummary({ reports: { 'A.txt': red, 'B.txt': green } });
  assert.deepStrictEqual(verdict.slice(0, 7), ['all', '3-shadow-all', 'FAIL', '20', '2', '1', '0']);
  assert.match(summary, /### Verdict: FAIL/);
  assert.match(summary, /\| 20 \| 2 \| 1 \| 0 \|/);
  assert.match(stdout, /::warning .*FAIL: 2 failures and 1 errors over 20 tests/);
});

test('no Surefire report at all is NO RESULT, never a pass', () => {
  const { verdict, stdout } = runSummary({ reports: {} });
  assert.strictEqual(verdict[2], 'NO RESULT');
  assert.match(stdout, /::warning .*the shadow suite did not run/);
});

test('a build that died with no red test is NO RESULT, not a pass', () => {
  const { verdict } = runSummary({ reports: { 'A.txt': green }, runOutcome: 'failure' });
  assert.strictEqual(verdict[2], 'NO RESULT');
  assert.match(verdict[7], /the Maven step did not succeed/);
});

test('context load failures are carried into the verdict note', () => {
  const reports = { 'A.txt': `${green}Failed to load ApplicationContext\n` };
  const { verdict, stdout } = runSummary({ reports });
  assert.match(verdict[7], /1 Spring context load failures/);
  assert.match(stdout, /::warning .*Spring context failed to load/);
});

test('the shadow run does not ignore test failures', () => {
  // The whole point: with maven.test.failure.ignore the Maven step is green whatever happened,
  // which is the state nobody can read. continue_on_error on the matrix entry is what keeps a red
  // shard out of the run conclusion.
  const action = fs.readFileSync(ACTION, 'utf8');
  const argsLine = action
    .split('\n')
    .find((line) => line.includes('echo "args=-Dopenaev.tenant.active-tables='));
  assert.ok(argsLine, 'the shadow argument line is gone from action.yml');
  assert.ok(
    !argsLine.includes('maven.test.failure.ignore'),
    'the shadow run must not ignore test failures',
  );
});
