import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Guard for the CI wiring of the private `@filigran/design-system` git
 * dependency.
 *
 * Installing a private git dependency needs a credential. Where that credential
 * has to be declared depends on *how* the install runs, and a product typically
 * runs it three different ways at once: inside an image build, inside a
 * container launched by a workflow step, and directly on the runner. Each has
 * its own propagation rule, and a composite action cannot read the `secrets`
 * context at all — the value must be a declared input, wired at every caller.
 *
 * Enumerating those sites by hand is not reliable: it was done twice on this
 * migration and missed a call site both times. This test does the enumeration
 * instead, by walking the call graph rather than grepping a directory. It fails
 * when a site that needs the credential does not declare it, and — just as
 * importantly — when a site that does not need it declares it anyway, because
 * an unnecessary credential is unnecessary leak surface.
 *
 * Validated by mutation on two products with different CI topologies:
 *
 *   OpenCTI (installs inside images and containers) — 30 assertions green;
 *     dropping the secret from the composite build step, dropping the input at
 *     one caller, and dropping `-e` from a container install each produce
 *     exactly one failure.
 *   OpenAEV (installs on the runner via composite actions) — green; dropping
 *     the token at one caller produces one failure.
 *
 * To reuse this in another product, change the five constants below. Nothing
 * else is product-specific.
 *
 * `REPO_ROOT` additionally honours the `FDS_CI_GUARD_REPO_ROOT` environment
 * variable. That exists so the design-system library, which owns this file but
 * has no product CI of its own to guard, can run it against the fixture tree in
 * `process/artifacts/fixtures/product-ci/` and catch a regression here before a
 * product copies it. Leave the variable unset — as a product does — and the
 * resolution is exactly the `path.resolve('../..')` it has always been.
 */

/** The BuildKit secret id used in `RUN --mount=type=secret,id=…`. */
const SECRET_ID = 'fds_git_token';
/** The repository secret name, as referenced by `${{ secrets.… }}`. */
const SECRET_NAME = 'FDS_GIT_TOKEN';
/** The private package whose presence makes an install need the credential. */
const PRIVATE_PACKAGE = '@filigran/design-system';
/** Repository root, relative to the directory vitest runs in. */
const REPO_ROOT = process.env.FDS_CI_GUARD_REPO_ROOT
  ? path.resolve(process.env.FDS_CI_GUARD_REPO_ROOT)
  : path.resolve('..');
/** Build stages that reach the frontend install. Empty ⇒ only the final stage. */
const STAGES_REACHING_INSTALL = ['front-builder'];

/**
 * PRODUCT-SPECIFIC ADDITION (not part of the upstream artifact).
 *
 * Call sites that must NEVER receive the credential, with the reason.
 *
 * The guard's composite-call rule is "every caller of an action that declares
 * the input passes it". That is the right default, but it cannot express a site
 * that must stay unarmed for a security reason, and this product has one.
 *
 * `deploy-feature-branch-build.yml` checks out untrusted PR code
 * (`ref: head_sha`) and then resolves `./.github/actions/docker-build` from that
 * same untrusted tree, so any credential handed to it is attacker-controlled. A
 * read token for the private design-system repository must not cross that
 * boundary. This is not awaiting arbitration: the structural resolution is the
 * publication of the library to npm, which removes the token altogether and
 * makes this workflow work with no secret at all. Until then, feature-branch
 * deploys touching the frontend fail at the install step, which is accepted.
 *
 * These sites are not merely skipped — they are asserted to be free of the
 * credential, so the prohibition is enforced rather than waived, and every
 * other call site stays covered by the rule above.
 */
const NEVER_ARMED: ReadonlyArray<{
  source: string;
  action: string;
}> = [
  {
    source: path.join('.github', 'workflows', 'deploy-feature-branch-build.yml'),
    action: 'docker-build',
  },
];

const isNeverArmed = (source: string, action: string): boolean =>
  NEVER_ARMED.some(entry => entry.source === source && entry.action === action);

const CI_ROOT = path.join(REPO_ROOT, '.github');

const listYamlFiles = async (dir: string): Promise<string[]> => {
  const entries = await readdir(dir, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map(async (entry) => {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) return listYamlFiles(full);
      return /\.ya?ml$/.test(entry.name) ? [full] : [];
    }),
  );
  return nested.flat();
};

const read = async (file: string): Promise<string> => {
  try {
    return await readFile(file, 'utf8');
  } catch {
    return '';
  }
};

/** Splits a YAML job body into its individual `- name:` / `- uses:` steps. */
const splitSteps = (content: string): string[] =>
  content.split(/^\s*-\s+(?=(?:name|uses):)/m).slice(1);

/**
 * Resolves a `file:` value that may contain `${{ … }}` interpolation.
 *
 * `file: platform/Dockerfile${{ inputs.suffix }}` denotes a *family* of
 * Dockerfiles, not one path. A naive `(\S+)` capture does not even match it —
 * the interpolation contains spaces — so the step is silently dropped from the
 * enumeration. That is exactly how the composite action that builds the product
 * image escaped an earlier version of this guard.
 */
const resolveBuildFiles = async (rawFile: string): Promise<string[]> => {
  const pattern = rawFile.replace(/\$\{\{[^}]*\}\}/g, '\u0000');
  if (!pattern.includes('\u0000')) return [pattern];
  const dir = path.dirname(pattern);
  const base = path.basename(pattern);
  const [prefix, ...rest] = base.split('\u0000');
  // NUL is used deliberately as a sentinel for an interpolation, so it cannot
  // collide with any real path character. Stripping it is part of that scheme.
  // eslint-disable-next-line no-control-regex
  const suffix = rest.join('').replace(/\u0000/g, '');
  const entries = await readdir(path.join(REPO_ROOT, dir)).catch(() => [] as string[]);
  return entries
    .filter(name => name.startsWith(prefix) && name.endsWith(suffix))
    .map(name => path.join(dir, name));
};

const dockerfileNeedsSecret = async (file: string): Promise<boolean> =>
  (await read(path.join(REPO_ROOT, file))).includes(`id=${SECRET_ID}`);

interface BuildStep {
  source: string;
  rawFile: string;
  files: string[];
  body: string;
  target?: string;
}

interface CompositeCall {
  source: string;
  action: string;
  body: string;
}

interface ContainerRun {
  source: string;
  name: string;
  body: string;
}

/**
 * Strips YAML comment lines.
 *
 * Without this, a step whose comment *explains* the wiring — "the token is
 * passed by name (-e FDS_GIT_TOKEN)" — satisfies the check while the wiring
 * itself is gone. A guard that a sentence can satisfy is not a guard.
 */
const withoutComments = (body: string): string =>
  body
    .split('\n')
    .filter(line => !/^\s*#/.test(line))
    .join('\n');

const yamlFiles = await listYamlFiles(CI_ROOT);

const buildSteps: BuildStep[] = [];
const compositeCalls: CompositeCall[] = [];
const containerRuns: ContainerRun[] = [];

for (const yamlFile of yamlFiles) {
  // eslint-disable-next-line no-await-in-loop
  const content = await read(yamlFile);
  const source = path.relative(REPO_ROOT, yamlFile);
  for (const body of splitSteps(content)) {
    if (body.includes('docker/build-push-action')) {
      const rawFile = body.match(/^\s*file:\s*(.+?)\s*$/m)?.[1];
      if (rawFile) {
        buildSteps.push({
          source,
          rawFile,
          // eslint-disable-next-line no-await-in-loop
          files: await resolveBuildFiles(rawFile),
          body,
          target: body.match(/^\s*target:\s*(\S+)\s*$/m)?.[1],
        });
      }
    }
    const composite = body.match(/uses:\s*\.\/\.github\/actions\/(\S+)/)?.[1];
    if (composite) compositeCalls.push({
      source,
      action: composite,
      body,
    });
    if (/docker\s+run/.test(body)) {
      containerRuns.push({
        source,
        name: body.match(/^name:\s*(.+?)\s*$/m)?.[1] ?? '(unnamed)',
        body,
      });
    }
  }
}

/** Composite actions that declare an input for the credential. */
const compositeInputs = new Map<string, string>();
for (const yamlFile of yamlFiles) {
  if (!/\/actions\/[^/]+\/action\.ya?ml$/.test(yamlFile)) continue;
  // eslint-disable-next-line no-await-in-loop
  const content = await read(yamlFile);
  const name = path.basename(path.dirname(yamlFile));
  const input = content.match(/^\s{2}(\S*(?:fds|design.?system)\S*token\S*):/im)?.[1];
  if (input) compositeInputs.set(name, input);
}

/**
 * The directories a container command actually works in.
 *
 * A workflow names them in three different ways — `cd` inside the script, the
 * host side of a `-v` mount, and `-w` — and the paths it prints are container
 * paths, prefixed by whatever the mount decided. Rather than model that, each
 * candidate is trimmed one leading segment at a time until it resolves to a
 * real manifest in the repository.
 */
const workspaceDirsOf = (body: string): string[] => {
  // Interpolations contain spaces (`${{ github.workspace }}/front:/opt/front`),
  // which breaks any `\S+` capture — the same defect that made an earlier
  // version of this guard skip the composite build step. Remove them first.
  const flat = body.replace(/\$\{\{[^}]*\}\}/g, '');
  const raw = [
    ...[...flat.matchAll(/^\s*cd\s+(\S+)/gm)].map(m => m[1]),
    ...[...flat.matchAll(/-v\s+(\S+?):/g)].map(m => m[1]),
    ...[...flat.matchAll(/-w\s+(\S+)/g)].map(m => m[1]),
  ];
  const candidates = new Set<string>();
  for (const entry of raw) {
    const segments = entry.replace(/^\/+/, '').split('/').filter(Boolean);
    for (let i = 0; i < segments.length; i += 1) {
      candidates.add(segments.slice(i).join('/'));
    }
  }
  return [...candidates];
};

/** Does the install performed by this container command need the credential? */
const containerRunNeedsSecret = async (body: string): Promise<boolean> => {
  // Global installs (`npm install -g corepack`) do not materialise the
  // workspace's dependencies and need no credential. Only a workspace install
  // resolves the private package.
  const installsWorkspace = body
    .split('\n')
    .filter(line => !/(^|\s)(-g|--global)(\s|$)/.test(line))
    .some(line => /(yarn install|npm ci|npm install|pnpm install)/.test(line));
  if (!installsWorkspace) return false;
  for (const dir of workspaceDirsOf(body)) {
    // eslint-disable-next-line no-await-in-loop
    const manifest = await read(path.join(REPO_ROOT, dir, 'package.json'));
    if (manifest.includes(PRIVATE_PACKAGE)) return true;
  }
  return false;
};

describe('CI wiring for the private design-system dependency', () => {
  it('found the call graph it is meant to guard', () => {
    expect(buildSteps.length + containerRuns.length).toBeGreaterThan(0);
  });

  it.each(buildSteps)(
    '$source builds $rawFile with the secret it needs',
    async ({ files, body, target }) => {
      const reachesInstall
        = target === undefined || STAGES_REACHING_INSTALL.some(stage => target.startsWith(stage));
      const needs
        = reachesInstall && (await Promise.all(files.map(dockerfileNeedsSecret))).some(Boolean);
      expect(withoutComments(body).includes(`${SECRET_ID}=`)).toBe(needs);
    },
  );

  it.each(
    compositeCalls.filter(
      ({ source, action }) => compositeInputs.has(action) && !isNeverArmed(source, action),
    ),
  )(
    '$source passes the credential to ./.github/actions/$action',
    ({ action, body }) => {
      const wiring = withoutComments(body);
      expect(wiring).toContain(`${compositeInputs.get(action)}:`);
      expect(wiring).toContain(`secrets.${SECRET_NAME}`);
    },
  );

  // PRODUCT-SPECIFIC ADDITION (not part of the upstream artifact) — see NEVER_ARMED.
  it.each(compositeCalls.filter(({ source, action }) => isNeverArmed(source, action)))(
    '$source never passes the credential to ./.github/actions/$action',
    ({ body }) => {
      expect(withoutComments(body)).not.toContain(`secrets.${SECRET_NAME}`);
    },
  );

  // PRODUCT-SPECIFIC ADDITION — a stale exemption is worse than none: it would
  // silently un-guard a live call site. Every entry must match something real.
  it.each(NEVER_ARMED)('the $source / $action exemption still matches a real call site', ({ source, action }) => {
    expect(compositeCalls.some(call => call.source === source && call.action === action)).toBe(true);
  });

  it.each(containerRuns)(
    '$source / $name runs its container with the credential it needs',
    async ({ body }) => {
      expect(new RegExp(`-e\\s+${SECRET_NAME}\\b`).test(withoutComments(body))).toBe(
        await containerRunNeedsSecret(body),
      );
    },
  );
});
