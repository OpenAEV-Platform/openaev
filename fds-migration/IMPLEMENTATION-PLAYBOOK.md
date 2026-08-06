# Implementation playbook — adopting a `@filigran/design-system` component in a product

**Audience.** A developer who has never wired the Filigran design system into a
product before, and who has none of the context of the pilot that produced this
document. Follow it top to bottom.

**Scope.** Everything from "the library exists somewhere" to "a green CI on a
pull request that ships a library component in production code". The worked
example throughout is the OpenAEV navigation pilot (the legacy MUI left menu
replaced by the library's `Navbar`); every step is written so it transposes to
any other component.

**Rule this document is held to.** If a step required guessing or improvising
during the pilot, the guess is written down here as the answer. Where the
library's own documentation disagreed with reality, reality is recorded and the
gap is filed in [`LIBRARY-FEEDBACK.md`](./LIBRARY-FEEDBACK.md).

**What it costs, honestly — and how honestly the figure is known.** The first
integration in a product is measured in days, not hours, and most of that time
is *not* writing the component.

Read the following as a **reconstruction from milestones, not a measurement**.
Nobody ran a stopwatch during the pilot; the split was rebuilt afterwards from
commit and checkpoint timestamps, and one block of roughly **44 % of the elapsed
time is undifferentiated** — it cannot be attributed to a named activity. Any
finer breakdown you may see quoted is invented. What survives that caveat is the
**shape**, and the shape is the useful part:

> Roughly **half** the time goes before there is an implementation that
> validates locally. Roughly **half** goes after — visual verification, style
> diffing against the documentation site, and converging the end-to-end tests.

That second half is the one this playbook covered worst, and it is where the
next pilot will lose its time: green types, green lint and green unit tests say
nothing about a rail that renders 22px wide or a customer accent that stopped
propagating. Budget for it explicitly instead of treating it as a tail.

As an order of magnitude for the first half: plumbing that only has to be done
once (authentication, CI wiring, install, stylesheet, theme bridge) ≈ half a
day; reading the component's real API and building the adapter ≈ a day; deleting
the legacy code and fixing the tests ≈ half a day. Once the plumbing exists,
adopting the *next* component in the same product is a small fraction of that,
and a [pin bump](#the-pin-bump-exercise) is under twenty minutes.

---

## Table of contents

1. [Prerequisites](#1-prerequisites)
2. [Step 0.5 — Read the previous pilot's implementation](#step-05--read-the-previous-pilots-implementation)
3. [Step 1 — Pick and freeze the pin](#step-1--pick-and-freeze-the-pin)
4. [Step 2 — Make CI able to authenticate](#step-2--make-ci-able-to-authenticate)
5. [Step 3 — Make CI actually run on your branch](#step-3--make-ci-actually-run-on-your-branch)
6. [Step 4 — Install the library](#step-4--install-the-library)
7. [Step 5 — Import the stylesheet and add the host prerequisites](#step-5--import-the-stylesheet-and-add-the-host-prerequisites)
8. [Step 5b — Diff against the library's own documentation site](#step-5b--diff-against-the-librarys-own-documentation-site)
9. [Step 6 — Bridge the theme](#step-6--bridge-the-theme)
10. [Step 6b — Audit the product's custom theme](#step-6b--audit-the-products-custom-theme)
11. [Step 7 — Read the component's real API before designing anything](#step-7--read-the-components-real-api-before-designing-anything)
12. [Step 8 — Build the product adapter](#step-8--build-the-product-adapter)
13. [Step 9 — Delete the code you replaced](#step-9--delete-the-code-you-replaced)
14. [Step 10 — Tests](#step-10--tests)
15. [Step 11 — File what the library is missing](#step-11--file-what-the-library-is-missing)
16. [Step 12 — Run the product for the visual checkpoint](#step-12--run-the-product-for-the-visual-checkpoint)
17. [Final verification checklist](#final-verification-checklist)
18. [The checkpoint loop](#the-checkpoint-loop--a-review-that-changes-its-mind-is-the-process-working)
19. [The pin-bump exercise](#the-pin-bump-exercise)
20. [What belongs to the library vs. to the product](#what-belongs-to-the-library-vs-to-the-product)

**Companion files.** [`PLAYBOOK-DEFECTS.md`](./PLAYBOOK-DEFECTS.md) — every
defect found by running this playbook against a real product, with severities
and status. [`artifacts/ci-design-system-secret.test.ts`](./artifacts/ci-design-system-secret.test.ts)
— the CI credential guard to copy into your product's test suite (Step 2).

---

## 1. Prerequisites

Get all four of these before writing a line of code. Each one has blocked the
pilot at least once.

### 1.1 Read access to a private repository, over HTTPS

`XTM-Foundation/filigran-design-system` is private, and it is consumed as a
**git dependency** — not from a registry. Your git client must be able to clone
it over HTTPS non-interactively.

```bash
# Verify. If this prompts for credentials or fails, stop and fix it first.
git ls-remote https://github.com/XTM-Foundation/filigran-design-system.git HEAD
```

If it fails, configure a Personal Access Token with `repo` scope:

```bash
git config --global url."https://x-access-token:<YOUR_PAT>@github.com/".insteadOf "https://github.com/"
```

> **Trap.** SSH access is not enough. Package managers resolve the dependency
> over HTTPS regardless of your personal `~/.gitconfig` remote preferences.

### 1.2 A CI secret named `FDS_GIT_TOKEN`

Same problem, on the runner. CI has no `~/.gitconfig`, so **the install step
will fail on CI even though it works on your machine.** The secret must exist
in the product repository *before* you open your pull request. Wiring it is
[Step 2](#step-2--make-ci-able-to-authenticate).

It is a repository secret (not an environment secret), named exactly
`FDS_GIT_TOKEN`, holding a token with read access to
`XTM-Foundation/filigran-design-system`. As of this pilot it is posted in both
`OpenAEV-Platform/openaev` and `OpenCTI-Platform/opencti`. Confirm before you
start:

```bash
gh secret list --repo <owner>/<product-repo> | grep FDS_GIT_TOKEN
```

### 1.3 The target branch

Design-system work targets **`design-system/current`**, never `main`.

Before branching, make sure `design-system/current` is up to date with `main`:

```bash
git fetch origin
git log --oneline --left-right --count origin/design-system/current...origin/main
# Second number > 0 means design-system/current is behind main.
```

If it is behind, sync it with a **direct merge, pushed** — never through a pull
request:

```bash
git checkout design-system/current && git pull
git merge origin/main -m "chore(fds-migration): merge origin/main into design-system/current"
git push origin design-system/current
```

> **Why not a pull request.** The repository's merge settings force squash
> merges. A squashed `main` → `design-system/current` sync rewrites history into
> a single unrelated commit, so `main` never becomes an ancestor and every later
> sync re-conflicts. A direct merge commit keeps the topology intact.

Then branch:

```bash
git checkout -b <your-branch> origin/design-system/current
```

### 1.4 A pin, and the knowledge that it will move

You will pin an exact library commit. That pin is a snapshot: library work
continues, and you will bump it later. Read
[The pin-bump exercise](#the-pin-bump-exercise) **now**, not at the end — it
changes how you write the code (what you mark as temporary, and how).

---

## Step 0.5 — Read the previous pilot's implementation

Not its playbook: its **source**. The playbook carries the method; the source
carries the answers to the integration problems the method does not predict.
Every pilot after the first inherits a body of already-solved problems, and the
only reason to solve one twice is not having looked.

**Where to look.** The pilot index below gives, for each pilot, the repository,
the branch, and the exact directory.

| Pilot | Product | Repository / branch | Implementation directory | Feedback filed |
|---|---|---|---|---|
| 1 | OpenAEV | `openaev` / `sandyghs-supreme-bassoon` | `openaev-front/src/components/common/menu/navbar/` — `AppNavbar`, `MadeByFiligran`, `NavbarRowContent`, `useNavbarState`, `nav-menu-model` | `fds-migration/LIBRARY-FEEDBACK.md` (14 entries) |
| 2 | OpenCTI | `opencti` / `fds-navbar`, base `design-system/current` | `opencti-platform/opencti-front/src/private/components/nav/` — `NavBar`, `MadeByFiligran`, `useNavMenu`, `navBarConstants` | `fds-migration/LIBRARY-FEEDBACK.md` (11 entries) |

> **Every pilot adds its own row to this table in the same pull request that
> ships it** — product, repository, branch, implementation directory, feedback
> file. A pilot that does not appear here does not exist for the next one.
> This is checked in the [final verification checklist](#final-verification-checklist),
> so that forgetting it fails something.

The row names a **directory**, not a pull-request number, on purpose: a number
ages out of usefulness the moment the branch merges, and a path can be searched.

**What to read, in this order.** For the most recent pilot:

1. the component that hosts the library component
   (pilot 1: `AppNavbar.tsx`; pilot 2: `NavBar.tsx`) — read its **inline
   comments first**: every host compensation is documented there with its
   reason and its removal test;
2. every sibling file in the same directory (state hooks, menu model, row
   content, satellite components such as `MadeByFiligran.tsx`);
3. the host stylesheet
   (pilot 1: `openaev-front/src/static/css/design-system-host.css`;
   pilot 2: `opencti-front/src/static/css/design-system-host.css`) — the rules
   there are the compensations that could not be expressed inline;
4. `fds-migration/LIBRARY-FEEDBACK.md` — the library gaps already filed, with
   the compensation each one required;
5. `fds-migration/IMPLEMENTATION-LOG.md` — the dated reds and the traps found.

**Commands.** The pilot's branch does not need to be checked out:

```bash
# from the previous pilot's repository
git fetch origin <branch>
git ls-tree -r --name-only origin/<branch> -- <implementation directory>
git show origin/<branch>:<path to a file>
```

**Success criterion.** You can name, without looking again: the compensations
the previous pilot had to write, the library entries it filed, and the traps it
dated. If you cannot, you have not read it.

**Time budget.** 30 to 45 minutes. Pilot 2 skipped this step and paid for it
with a full checkpoint round trip: three of its four checkpoint findings —
full-height and fixed rail, signature sizing, collapsed emblem — were already
solved in pilot 1's source, in the same collection of repositories, and were
rediscovered from scratch. One of the three was delivered wrong to the reviewer
before being found.

### Standing rule for the whole implementation

> **Before solving any integration problem, check the previous pilots.**
> Grep their implementation directory (the index above gives the exact paths)
> for the symptom, the property, or the API you are fighting with. If a pilot
> already solved it, apply **its** solution rather than inventing a second one,
> and cite it in the code comment:
>
> ```
> // Same technique as the OpenAEV pilot
> // (openaev-front/src/components/common/menu/navbar/AppNavbar.tsx).
> ```
>
> Two different solutions to the same library gap are two things to maintain and
> two different bug reports. If you deliberately diverge, say why in the comment.

Useful greps, from the previous pilot's repository:

```bash
git grep -n "<css property, prop name, or symptom>" origin/<branch> -- <implementation directory>
git grep -n "position: 'sticky'\|100dvh\|z-index\|shrink-0" origin/<branch> -- <implementation directory>
```

---

## Step 1 — Pick and freeze the pin

**Purpose.** Depend on an exact, reproducible library commit.

```bash
git ls-remote https://github.com/XTM-Foundation/filigran-design-system.git main
```

Take the SHA of `main`'s head **at the moment you wire the dependency**. Write
it into your pull-request description. Do not pin a branch name, a tag, or a
commit that is not on `main`.

**Then prove the SHA installs, before you build anything on it.**

```bash
yarn add "@filigran/design-system@XTM-Foundation/filigran-design-system#commit=<SHA>"
ls node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.mjs
```

**Success criterion.** You have a 40-character SHA, `git ls-remote … main`
returns it, the install completes without a `YN0058 Packing the package failed`,
and `dist/index.mjs` exists on disk.

> **A green library CI does not prove the library is installable — verify the
> pin yourself.** This is not a hypothetical: it cost this pilot a bump, and it
> is the single most likely way your first day goes wrong.
>
> The two paths are genuinely different. The library's own pipeline builds
> **from its workspace**, with its own package manager and its own lockfile. You
> install **from its git tree**: your package manager clones the repository,
> resolves against the **root** manifest — no package manager can target a
> subdirectory of a git dependency — and runs its `prepare`/`prepack` script to
> build `dist` at install time. A dependency declared in the package's manifest
> but not the root one, or a lockfile the library's own tooling never touches,
> breaks you and nobody else. Every library check stays green.
>
> The library now runs an install proof from a blank project as a step of its
> required job, so this specific hole is closed. Keep the check anyway: it costs
> one command, it tells you *immediately* whether a red build is yours or
> inherited, and the guard only covers the failures someone has already thought
> of.
>
> If the install fails: read the `pack.log` whose path the `YN0058` line prints
> — the real error is one line inside it, not in yarn's summary — then **do not
> work around it.** There is no product-side version of "the package will not
> build". Report it and stay on the last pin that worked. See
> [the bump you cannot do](#worked-example--the-bump-you-cannot-do) for how that
> plays out end to end.

**Traps.**

- Pinning a commit that only exists on a library feature branch works locally
  and is a trap: it can be force-pushed or deleted, and it has not been
  reviewed. If the library fix you need is not merged yet, **you are blocked** —
  say so, do the work that does not depend on it, and wire the dependency when
  it merges.
- The library's `main` moves during your work. That is fine and expected. Do
  **not** chase it mid-pilot: pin once, finish, then bump deliberately.

---

## Step 2 — Make CI able to authenticate

**Purpose.** Let every CI job that runs an install clone the private library.

The mechanism is a single git config line that must run **before** every
install:

```bash
git config --global url."https://x-access-token:${FDS_GIT_TOKEN}@github.com/".insteadOf "https://github.com/"
```

Do not repeat that line by hand in a dozen places. Put it in one composite
action and reuse it. In OpenAEV, that is
`.github/actions/setup-fds-auth/action.yml`:

```yaml
name: Setup design-system git auth
description: Configures git so the private @filigran/design-system git dependency can be installed.
inputs:
  token:
    description: A token with read access to XTM-Foundation/filigran-design-system.
    required: true
runs:
  using: composite
  steps:
    - shell: bash
      env:
        FDS_GIT_TOKEN: ${{ inputs.token }}
      run: |
        if [ -z "${FDS_GIT_TOKEN}" ]; then
          echo "::error::FDS_GIT_TOKEN is empty. The @filigran/design-system git dependency cannot be installed."
          exit 1
        fi
        git config --global url."https://x-access-token:${FDS_GIT_TOKEN}@github.com/".insteadOf "https://github.com/"
```

Then, for **every** action or workflow that runs an install:

1. add an input (`fds-git-token`) if it is a composite action;
2. add `- uses: ./.github/actions/setup-fds-auth` with that token immediately
   before the install step;
3. update every call site to pass `fds-git-token: ${{ secrets.FDS_GIT_TOKEN }}`.

**Success criterion.** Every leaf of the call graph that installs is armed, and
the guard test below is green. Do **not** use a flat grep as your inventory —
see "Enumerate by walking the call graph" immediately below, which is the part
of this step that two pilots got wrong.

### Enumerate by walking the call graph, not by grepping a directory

A flat `grep -rn "yarn install" .github/` is not an inventory. It is how this
step was written for the first pilot, and it missed an install site **twice on
the same repository — once inside its own correction**. That is a structural
failure, not a typo: the grep answers "which files mention an install", while
the question is "which leaves of the call graph run one, and does the credential
reach each of them".

Two whole classes escape a directory grep:

- **Installs that are not in `.github/` at all.** A `Dockerfile` runs
  `yarn install` inside the image. Scope the grep to `.github/` and you produce
  a "complete" inventory that omits the builds that actually ship the product.
- **Installs reached indirectly.** A workflow calls a composite action, which
  runs the build. The install site is one file; the place the credential must be
  wired is another; and there is one such place *per caller*.

**Three mechanisms, three different rules.**

| Where the install runs | How the credential gets there | What breaks it |
|---|---|---|
| Inside a `Dockerfile` (`RUN yarn install`) | A BuildKit build secret, mounted for that `RUN` only | Host configuration does not cross the image boundary |
| Inside a `docker run` in a workflow step | An explicit `-e` on that run | The runner's `~/.gitconfig` is not in the container |
| Directly on the runner | The composite action above, or ordinary `env:` | — |

**Two propagation rules that have nothing to do with each other.**

- A **reusable workflow** (`uses: ./.github/workflows/x.yml`) does not receive
  the caller's secrets unless the caller writes `secrets: inherit`.
- A **composite action** (`uses: ./.github/actions/x`) *cannot read the
  `secrets` context at all*, ever, whatever the caller does. The value must be a
  declared `input`, wired at **every single caller**, one by one.

The second rule is the trap, because the failure looks like something else:

```
##[warning]fds_git_token= is not a valid secret
cat: can't open '/run/secrets/fds_git_token': No such file or directory
```

That reads as *the secret does not exist*. It does exist. It was simply not
handed to that call site. **Before you re-check the secret in repository
settings, re-check the caller.**

**Walk the graph like this.** Start from the leaves — everything that builds an
image or launches a container — then climb to every caller:

```bash
# 1. Leaves: installs inside images. Note: no --include filter, and the search
#    starts at the repository root, not at .github/.
grep -rn "yarn install\|npm ci\|pnpm install" --include="Dockerfile*" .

# 2. Leaves: installs on the runner or inside a container launched by a step
grep -rn "yarn install\|npm ci\|pnpm install" .github/

# 3. Leaves: image builds, wherever they are declared (workflows AND actions)
grep -rn "docker/build-push-action\|docker run" .github/

# 4. Climb: every caller of every composite action
for a in $(ls .github/actions); do
  echo "== $a"; grep -rn "uses: ./.github/actions/$a" .github/workflows/
done

# 5. Climb: reusable workflows, and whether they inherit secrets
grep -rn -A6 "uses: ./.github/workflows/" .github/workflows/ | grep "secrets:"
```

> **A caution about step 1.** Write it exactly as shown. `--include` filters the
> *whole* search, so appending `--include="Dockerfile*"` to a command that also
> searches `.github/` silently drops every workflow hit — on OpenCTI, 23 of them,
> leaving 6. A command that filters out what it claims to search is worse than
> no command, because it returns confidently. Run the two searches separately.

For step 4, open **each** hit and confirm the credential input is present. Do
not trust that "the build workflows are wired": the main CI entry point often
calls the same action with a shorter argument list, and is easy to overlook
precisely because it is not named like a build workflow. That is the exact file
that was missed on OpenCTI.

### Arm the containerised install sites

The composite action above covers installs that run **on the runner**. The other
two mechanisms need their own wiring, and neither inherits anything.

**Inside a `Dockerfile`.** Use a BuildKit build secret, mounted for the single
`RUN` that installs. It is present during that layer and stored in none:

```dockerfile
RUN --mount=type=secret,id=fds_git_token \
    set -eu; \
    if [ ! -s /run/secrets/fds_git_token ]; then \
      echo "fds_git_token build secret is missing or empty" >&2; exit 1; \
    fi; \
    git config --global url."https://x-access-token:$(cat /run/secrets/fds_git_token)@github.com/".insteadOf "https://github.com/"; \
    yarn install --frozen-lockfile; \
    git config --global --unset-all url."https://x-access-token:$(cat /run/secrets/fds_git_token)@github.com/".insteadOf
```

Note `set -eu`, **not** `set -eux`: with `-x` the shell echoes the expanded
command, token included, into the build log.

And on the build step:

```yaml
- uses: docker/build-push-action@v6
  with:
    file: platform/Dockerfile
    secrets: |
      fds_git_token=${{ secrets.FDS_GIT_TOKEN }}
```

An unprovided build secret is **no file at all**, not an empty one — hence
`[ ! -s … ]` rather than a string test.

**Inside a `docker run` launched by a workflow step.** Pass the variable **by
name**, and keep the script single-quoted:

```yaml
- run: |
    docker run --rm -e FDS_GIT_TOKEN -v ${{ github.workspace }}:/src -w /src node:22 \
      sh -c 'set -eu
             git config --global url."https://x-access-token:${FDS_GIT_TOKEN}@github.com/".insteadOf "https://github.com/"
             yarn install --frozen-lockfile'
  env:
    FDS_GIT_TOKEN: ${{ secrets.FDS_GIT_TOKEN }}
```

`-e FDS_GIT_TOKEN` with no value tells Docker to forward the runner's variable.
Writing `-e FDS_GIT_TOKEN=${{ secrets.… }}`, or double-quoting the `sh -c` body,
puts the token on a command line that any process on the runner can read.

### Then stop enumerating by hand — ship the guard test

Everything above tells you how to *think* about the problem. It still relies on
a human being exhaustive, twice, under time pressure. Do not rely on that: the
enumeration was performed carefully by two pilots and missed a site both times.

Copy `fds-migration/artifacts/ci-design-system-secret.test.ts` into the
product's own test suite. It walks the call graph and asserts, for every leaf:

1. every image build declares the build secret **exactly when** the Dockerfile
   it builds needs one — and not otherwise, because a credential handed to a
   build that does not install is leak surface for nothing;
2. every caller of a composite action that declares a credential input actually
   passes it, from `secrets.<NAME>`;
3. every container run that installs a workspace depending on the private
   package receives the credential by `-e`.

Adapt the five constants at the top of the file; nothing else is
product-specific. Put the file where the product's runner will pick it up
(OpenAEV's vitest only collects `src/__tests__/**`, so it belongs there, not in
`fds-migration/`).

**Why this replaces the human enumeration.** It was validated by mutation on two
products with different topologies. On OpenCTI: 30 assertions green, and each of
three regressions — dropping the secret from the composite build step, dropping
the input at one caller, dropping `-e` from a container install — produces
exactly one failure. On OpenAEV, which installs on the runner instead: green,
and dropping the token at one caller fails.

**Three defects it was built to survive**, each of which made an earlier version
report success while the wiring was broken. They are worth knowing, because they
are the ways *any* such check lies:

- **Interpolated values break naive captures.** `file: platform/Dockerfile${{ inputs.suffix }}`
  contains spaces, so a `(\S+)` capture does not match it at all and the step is
  dropped from the enumeration in silence. The first version of this guard
  skipped the composite action that builds the product image — the very site
  that had been missed twice by hand. Strip `${{ … }}` before parsing paths.
- **Comments satisfy string checks.** A step whose comment *explains* the wiring
  ("the token is passed by name, `-e FDS_GIT_TOKEN`") passes a check for
  `-e FDS_GIT_TOKEN` after the wiring itself has been deleted. Strip comment
  lines before asserting.
- **Not every install needs the credential.** `npm install -g corepack` installs
  nothing from the private registry. Decide "needs the credential" from the
  manifest of the directory being installed, not from the presence of the word
  *install*.

**Traps.**

- **Do not put the token on the command line** (`git config … ${{ secrets.X }}`
  inline). Pass it through the step's `env:` so it is not part of a logged
  command. In a `docker run`, pass it **by name** (`-e FDS_GIT_TOKEN`, no value)
  and keep the `sh -c` body **single-quoted**: double quotes make the *runner*
  expand it onto the docker command line, where any process can read it.
- **`set -x` prints the expanded credential.** If the container script already
  runs `set -eux` — many do — the `git config … insteadOf` line lands in the
  build log with the token in it. Bracket it with `set +x` … `set -x`.
- **The image must contain `git`.** A git-hosted dependency is fetched by git,
  and `node:*-alpine` ships without it. Verify with
  `docker run --rm <image> git --version`; add `apk --no-cache add git`
  (Alpine) or `git-core` (UBI/RHEL). Debian-based `node:22` already has it.
  Install it in the earliest **shared** stage, not the leaf stage, if that stage
  is exported as an artefact and reused later. Without git the symptom looks
  like an authentication failure.
- **Only stages that reach the install need the secret.** Check each build
  step's `target:`. A `builder` stage that stops before any install needs
  nothing, and arming it adds leak surface for nothing.
- **Alpine / container steps cannot use the composite action** — they run with
  a different shell (`shell: alpine.sh`), and a composite action's `shell: bash`
  step will not execute there. Inline the same two lines inside those blocks,
  with `env: FDS_GIT_TOKEN: ${{ secrets.FDS_GIT_TOKEN }}` on the step.
- **Always keep the empty-token guard.** A missing or unpropagated secret
  otherwise surfaces as a confusing "repository not found" from the package
  manager, several minutes into the job.
- **Prove no token reached the image.** Cheap, and non-negotiable if you used a
  build secret:

  ```bash
  docker history --no-trunc <image> | grep -i <token-fragment> || echo "clean: history"
  docker run --rm --entrypoint sh <image> -c 'cat /root/.gitconfig 2>/dev/null' || echo "clean: gitconfig"
  ```

  A `RUN --mount=type=secret` never persists; a plain `ARG` or `ENV` always
  does. This check is what tells the two apart.
- **A secret that exists is not a secret that works.** The pilot's first CI run
  failed with the token correctly wired and correctly non-empty:

  ```
  Failed cloning the repository
  Remote Error: Write access to repository not granted.
  unable to access 'https://github.com/XTM-Foundation/filigran-design-system.git/':
  The requested URL returned error: 403
  ```

  Read the status code, it tells you where the problem is:

  | Symptom | Meaning |
  |---|---|
  | the empty-token guard fires | the secret is absent, or not propagated into that step/container |
  | credential prompt, or `could not read Username` | no credentials reached git — the `insteadOf` is missing or ran too late |
  | **403** | credentials *were* sent and refused — the token lacks access |
  | 404 on a repository you know exists | usually the same thing: a private repository is invisible to a token that cannot read it |

  A 403 is a **token grant** problem, not a wiring problem, and no change to
  the workflows will fix it. Ask whoever owns the secret to check, in order:
  the token can read the *library's* organisation (a fine-grained token scoped
  to the product's organisation cannot read another org's repositories); it
  carries `Contents: Read`; and, if the library's organisation enforces SAML
  single sign-on, that the token has been explicitly authorised for it.

  Verify the token in one command before blaming anything else — 200 means the
  token can read the repository, anything else means it cannot:

  ```bash
  curl -sS -o /dev/null -w '%{http_code}\n' \
    -H "Authorization: token <THE_TOKEN>" \
    https://api.github.com/repos/XTM-Foundation/filigran-design-system
  ```

  **Do this before opening the pull request.** It costs one command and saves a
  full red CI run.

---

## Step 3 — Make CI actually run on your branch

**Purpose.** A pull request into `design-system/current` must trigger the same
checks a pull request into `main` triggers.

Product CI workflows are usually filtered to `main` and release branches, so
`design-system/current` gets **no CI at all** — the pull request looks clean
because nothing ran.

```yaml
on:
  push:
    branches: [main, 'release/*', design-system/current]
  pull_request:
    branches: [main, 'release/*', design-system/current]
```

**Success criterion.** Push the branch, open the pull request, and confirm the
checks list is not empty.

**Trap.** Adding the branch to `pull_request` only is not enough if the
workflow's jobs are also gated on `push` events; add it to both.

---

## Step 4 — Install the library

**Purpose.** Get `@filigran/design-system` resolvable in the product.

The package is **not published to a registry**. It is installed from git, from
the repository root, which acts as an install proxy for the package inside the
monorepo. Two things are mandatory:

- the `@filigran/design-system@` **alias** — without it the package installs
  under the repository's own name and no import resolves;
- the `#commit=<SHA>` **pin**.

```bash
cd <product-front-directory>
yarn add '@filigran/design-system@XTM-Foundation/filigran-design-system#commit=<PIN_SHA>'
```

If the product uses Yarn Berry (Yarn 2+, check `packageManager` in
`package.json`), you must also allow the repository in `.yarnrc.yml`:

```yaml
# the design system is consumed as a pinned git dependency (private repo)
approvedGitRepositories:
  - 'https://github.com/XTM-Foundation/filigran-design-system.git'
```

**Success criterion.**

```bash
node -e "console.log(require.resolve('@filigran/design-system'))"
# → …/node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.js
ls node_modules/@filigran/design-system/packages/filigran-design-system/dist
# → index.css index.d.ts index.js index.mjs styles tokens
```

**Traps.**

- **`YN0080: … approvedGitRepositories`.** Yarn ≥ 4.10 blocks git dependencies
  that are not explicitly allowed. The entry must be the **exact URL string**
  Yarn resolved to (`https://github.com/…​.git`); a wildcard `'*'` is rejected.
- **`Assertion failed: Unsupported workflow`.** Yarn detects the *source*
  repository's package manager and refuses if it is not one it can bootstrap.
  This is a **library-side** condition, not something you can work around in the
  product. If you hit it, the library is missing its Yarn-consumer support —
  stop, report it, and wait for the fix. (The pilot hit exactly this, reported
  it, and had to wait for the library fix to land. It is fixed as of library
  commit `41a0a570a675f225ccefd366b1b1795869463f41`: **if your product uses
  Yarn Berry, pin at or after that commit**.)
- **The installed layout is not flat.** The package's `dist/` lives at
  `node_modules/@filigran/design-system/packages/filigran-design-system/dist/`.
  Never hand-write that path: the manifest's `exports` map handles it, so import
  `@filigran/design-system` and `@filigran/design-system/dist/index.css` and let
  the resolver do its job.
- `enableScripts: false` in `.yarnrc.yml` does **not** block the library's build
  — its `prepack` runs as part of git-dependency packing. No extra
  `dependenciesMeta` entry is needed.

---

## Step 5 — Import the stylesheet and add the host prerequisites

**Purpose.** The components are unstyled without the library stylesheet, and
partially broken without a host-side reset.

In the application entry point, after the product's other vendor stylesheets
and **before** the product's own:

```ts
import '@filigran/design-system/dist/index.css';
import './static/css/index.css';
import './static/css/design-system-host.css';
```

Then create `design-system-host.css`. **At a pin of `ad10875` it needs exactly
two rules.** Neither is library debt in the "the library is broken" sense —
both are what happens when a self-contained design system lands inside an
application that already has opinions.

```css
/* 1. HOST CONCERN. The product's own icons come from MUI, which renders them
      at 24px; the library's navigation rows are designed around 16px glyphs.
      Scoped to the navigation so no other MUI icon is affected. */
.app-navbar .MuiSvgIcon-root {
  font-size: 16px;
}

/* 2. COMPENSATION for library feedback #12 — remove at the pin that closes it.
      Every floating surface in the library is fixed at Tailwind's `z-50`, and
      Radix copies that value inline onto the portal wrapper it appends to
      <body>. This application's MUI top bar sits at z-index 1100, so every
      library menu, flyout and tooltip painted *underneath* it. 1300 is MUI's
      own `zIndex.modal` — the level the MUI popovers being replaced used.
      `!important` is required: the value Radix writes is inline.
      REMOVAL TEST: at a pin exposing a stacking hook (the ask is a
      `--fds-z-overlay` custom property), delete this rule, set the variable on
      :root, then open the ProductSwitcher menu over the header in both themes
      and both rail states and confirm the wrapper computes above 1100. */
body > [data-radix-popper-content-wrapper] {
  z-index: 1300 !important;
}
```

**Find this one before your reviewer does.** It is a single cause with many
symptoms — at this pin seven library surfaces share the hard-coded value
(tooltip, dialog overlay, dialog content, menu content, navbar flyout,
product-switcher dropdown, select content). If a checkpoint reports "the menu
goes under the header", do not fix the menu: enumerate the portal wrappers and
fix the stacking once.

```js
// In the browser console, with a menu open:
[...document.querySelectorAll('body > [data-radix-popper-content-wrapper]')]
  .map(w => getComputedStyle(w).zIndex);
// …and the value to beat, from the host's own chrome:
getComputedStyle(document.querySelector('header')).zIndex;
```

<details>
<summary><strong>History — the three rules this file used to need, and why you
should still know about them</strong></summary>

This pilot started at an earlier pin, where the same file carried three more
rules. Two were host prerequisites (**the library's stylesheet shipped no CSS
reset, so every element it rendered that the browser styles by default was wrong
in the host**) and one compensated a library layout defect. All three are now
fixed upstream and were deleted at the `d7ea4f2` bump.

```css
/* 1. Native controls: the library shipped utilities without Tailwind's
      preflight, and MUI's CssBaseline does not reset <button> either, so every
      library button painted the UA `buttonface` grey. */
button { background-color: transparent; border: 0; padding: 0;
         font: inherit; color: inherit; cursor: pointer; }

/* 2. Same cause: the UA styles <hr> with `border: 1px inset` on all four
      sides, so the library's separators rendered 2px and boxed instead of a
      1px rule. Scoped by class so MUI's own <hr> Divider was untouched. */
hr[class*="border-t"] { border-right-width: 0; border-bottom-width: 0;
                        border-left-width: 0; }

/* 3. Library defect, not a host conflict: the Navbar's scroll list did not
      mark its children shrink-0, so a menu taller than the rail was
      flex-compressed instead of scrolling. */
.app-navbar .overflow-y-auto > * { flex-shrink: 0; }
```

Keep reading them for the *category*, not the code: if you pin a library version
where a component still meets a browser default it never declared, you will
write the same kind of rule. Then file it, date it, and delete it at the bump —
see [the pin-bump exercise](#the-pin-bump-exercise). Use
[Step 5b](#step-5b--diff-against-the-librarys-own-documentation-site) to find
the rest of the family yourself rather than waiting for symptoms.

</details>

**Success criterion.** Render any library control built on a `<button>` and
inspect its computed `background-color`: it must be transparent, not
`rgb(239, 239, 239)`. A separator's computed border widths must be
`1px 0px 0px 0px`. A navigation row must measure its designed height (36px)
with the product's full menu loaded, at a short viewport. At a modern pin all
three hold with no host rule at all — if one does not, you have found a
regression worth reporting, not a rule to re-add silently.

**Traps.**

- **Historically the single most confusing failure of the whole integration.**
  When the library stylesheet shipped no preflight, every library button painted
  the user-agent `buttonface` grey with a 3D border. It looked like the library
  was broken; it was a host prerequisite that nothing documented. Fixed upstream
  — but the *shape* of the failure (a component meeting a browser default it
  never declared) will recur with any component the library has not yet hardened.
- **Do not write blanket element resets.** `hr { border-bottom-width: 0 }` looks
  harmless and flattens every MUI `Divider` in the application — MUI's Divider
  is an `<hr>` too. Scope host resets to something only the library emits (a
  class it always sets), not to the tag.
- **Any host rule you write here is temporary by default.** Write the removal
  condition *into the file*, next to the rule, together with the recipe that
  reproduces the symptom's worst case — the person doing the bump must be able
  to test it without rediscovering it.
- **Cascade layers.** If the product has unlayered global resets (e.g. a bare
  `a { text-decoration: none }`), they beat everything the library puts in a
  `@layer`, regardless of specificity. When a library style mysteriously does
  not apply, look for an unlayered product rule before suspecting the library.
- **Icon sizes.** If you feed the product's existing icon set into library
  slots, check the size. The library's navigation rows are designed around 16px
  glyphs; MUI's icons render at 24px. Scope the correction to your component —
  never globally:
  ```css
  .app-navbar .MuiSvgIcon-root { font-size: 16px; }
  ```

### 5.1 The stylesheet is not Tailwind — do not write utility classes

**This trap cost this pilot a silently squashed product logo that survived
several visual checkpoints.** Read it before you compose anything around the
library's slots.

`@filigran/design-system/dist/index.css` is the **compiled output of the
library's own components**. It contains the utilities the library happens to
render, and nothing else. If your product has no Tailwind build of its own —
this one does not — then a class you invent either exists there by coincidence
or does nothing at all. And a class that does not exist fails **silently**: no
build error, no console warning, no visual marker.

What that looked like here: a logo passed to a slot as

```tsx
<img src={theme.logo} className="h-7 w-full object-contain object-left" />
```

`h-7` and `w-full` exist (the library uses them), `object-contain` and
`object-left` do not. So the image got a fixed 28px height and 100% width with
**no** object-fit — i.e. stretched. Natural aspect ratio 5.118, rendered at
4.500: the product's own logo, squashed 12% horizontally, on every screen.

Audit yours before you trust it. Grep the stylesheets that are actually loaded
for every class your integration passes to a library component:

```bash
CSS=node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.css
# The compiled CSS escapes the characters Tailwind puts in class names: `mr-0.5`
# is written `.mr-0\.5`, `w-[100px]` is `.w-\[100px\]`. Strip those backslashes
# once, or every such class is reported INERT when it is not.
sed 's/\\//g' "$CSS" > /tmp/fds-classes.css
# Collect class names from every form the code uses: "…", {"…"}, {`…`} and the
# branches of a ternary inside a template literal. Anything less misses the
# collapsed-state classes, which are exactly the ones you cannot see by eye.
grep -rhoE 'className=(\{`[^`]+`\}|"[^"]+"|\{"[^"]+"\})' src/<your nav dir> \
  | sed -E 's/className=\{?`?"?//; s/`?\}?"?$//' \
  | tr ' ' '\n' | sed "s/[\${}?:']//g" | grep -E '^[a-zA-Z]' | sort -u \
  | while read -r c; do
      needle=$(printf '%s' "$c" | sed 's/[.[]/\\&/g')
      grep -q "\.$needle[^a-zA-Z0-9_-]" /tmp/fds-classes.css || echo "INERT: $c"
    done
```

Prove the audit before you trust it: feed it a class you know is absent
(`object-contain`, `object-left`) and check it is reported. An audit that
reports nothing because its extraction missed your files is worse than none.

Two false alarms to expect, and neither is a bug: your own hook class (here
`.app-navbar`, which lives in the host stylesheet) and any bare identifier the
extraction picks out of a ternary. Everything else on that list is a class that
does nothing.

Two findings worth internalising:

- **A class that "works" may be borrowed.** `h-3` resolved at an early pin only
  because an unrelated sibling package, `@filigran/chatbot/dist/styles.css`,
  happened to ship it — the design system did not. (It ships it at pin
  `ad10875`, so that particular class is no longer borrowed; the *category* is
  the point.) Audit against the design system's stylesheet alone: a class that
  only exists in a sibling package is an accidental cross-package dependency
  that breaks silently the day that package changes.
- **Do not "activate" an inert class when you remove it.** If a checkpoint has
  already approved the rendering, the inert class is part of what was approved.
  Decide deliberately whether the intent (here: stop stretching the logo) is a
  fix to apply or a change to raise.

**The rule that came out of it:** *token-bearing classes from the library are
fine — geometry you invent is inline.* `text-default-secondary`,
`text-content-caption`, `shrink-0`, `truncate` are the library's published
vocabulary and should be used. Product-specific sizing, fitting and positioning
goes in a `style` object, which is guaranteed to apply and is visible in review:

```tsx
<img src={theme.logo} style={{ height: 28, width: '100%',
     objectFit: 'contain', objectPosition: 'left center' }} />
```

**The third case, and the one the rule alone will mislead you on.** When
`asChild` forces you to re-compose a library row's internals
([Step 7](#step-7--read-the-components-real-api-before-designing-anything)),
you are not writing geometry — you are *reproducing the library's own row
anatomy*, layout classes included. Inlining those would desynchronise your row
from every future restyle. So copy them verbatim from the component's source,
keep them in one file so the debt stays countable
(`NavbarRowContent.tsx` here), and let the audit above prove they all exist:

```tsx
// Reproduced from the library's NavbarItem.tsx — layout classes included,
// on purpose. Not geometry: the library's published anatomy.
<span className={`flex-1 truncate text-left ${collapsed ? 'sr-only' : ''}`} />
```

The three-way test: **the library's token or anatomy → its class; geometry you
invented → inline style; a class you cannot find in the library's source →
neither, you made it up.**

Filed upstream as feedback #13 — the ask is that the consumer documentation say
`index.css` is not a Tailwind runtime, and that slots document the geometry
they expect of the host.

---

## Step 5b — Diff against the library's own documentation site

**Purpose.** Find the styling defects you cannot see. Both classes of bug above
were invisible to review: the markup is the library's, the class names are the
library's, and the result still looks plausible. The only way to catch them is
to compare **computed styles** against a host that is known-correct.

That reference exists: the library's documentation site uses the real
components and imports full Tailwind, so it has the preflight your product does
not. Run it at *your pinned commit* — not at `main`, or you will chase
differences that are really pin lag.

```bash
git clone https://github.com/XTM-Foundation/filigran-design-system.git /tmp/fdsdoc
cd /tmp/fdsdoc && git checkout <YOUR_PIN_SHA>
corepack enable && pnpm install --frozen-lockfile   # also builds the package (root `prepare`)
cd docs && npx next dev --port 3055
```

> **Two corrections the pilot paid for.** The root `pnpm install` already builds
> the package through the root manifest's `prepare` script — no separate
> `pnpm --filter … build` is needed, and the documentation site's own
> dependencies are *only* installed by that root install. And do **not** start it
> with `pnpm --filter ./docs dev -- --port 3055`: Next.js reads the passed-through
> `--port` as a project directory and dies with
> `Invalid project directory provided, no such directory: …/docs/--port`. Run
> `npx next dev --port <n>` from `docs/` instead.

With both running, drive a real browser (Playwright) and read
`getComputedStyle` on each equivalent element rather than eyeballing
screenshots. Compare, for both themes and both rail states: the rail itself,
rows (height, padding, gap, font size, line height, letter spacing, colours,
selected-state border), separators (thickness, colour, margins), submenus and
flyout panels, the `ProductSwitcher` trigger and menu, icons (box size, colour,
alignment) and tooltips.

Write the comparison as **one measurement function applied to both pages**, so a
difference can never come from measuring two different things:

```js
const MEASURE = ({ rootSel, rootIndex }) => { /* …read getComputedStyle… */ };
out.docs = await docsPage.evaluate(MEASURE, { rootSel: 'nav', rootIndex: 1 });
out.prod = await prodPage.evaluate(MEASURE, { rootSel: '.app-navbar', rootIndex: 0 });
```

Keep those scripts. They are re-run at every pin bump, and they are what turns a
bump from an afternoon into twenty minutes.

**Success criterion.** For every element rendered from the library's own
classes, the measured values are identical on both sides. Where they differ,
you must be able to name the cause — never "it looks close enough".

**How to read a difference.** Sort every gap into one of these, because the
action is different for each:

| The difference is | Meaning | What to do |
|---|---|---|
| your wrapper's own markup or classes | product integration bug | fix it in your branch |
| identical library classes, different computed value | host CSS bleed, or a missing reset | fix by isolation on the host, and add the mechanism to this playbook |
| present on `main` but not at your pin | pin lag | nothing — list it for the pin bump |
| no technical cause, the two simply differ by intent | design delta | escalate; it is not yours to decide |

**Traps.**

- **A shared class name does not mean a shared result.** Both defects found in
  this pilot showed *byte-identical* `class` attributes on both sides. Diff the
  computed value, never the markup.
- **Measure with the product's real data.** The row-compression defect only
  appears past a certain number of entries; with a short demo menu everything
  measures perfectly. Log in, load the real menu, and test at a short viewport.
- **Inherited font size is a red herring.** MUI sets `body { font-size: 14.4px }`,
  so anything inherited inside the navigation reads 14.4px against the
  documentation site's 16px. It changes nothing as long as the components use
  their own typography tokens — verify that rather than chasing it.
- **Check the reference is really in the state you think.** Forcing the
  documentation site's theme through `localStorage` alone did not switch it;
  its own theme toggle did — and both applications read a `light`/`dark` class
  on `<html>`, so toggling that class directly is a reliable way to measure both
  themes on both sides without hunting for two different toggles.
- **Playwright's `evaluate` takes exactly one argument.** Passing two throws
  `Too many arguments. If you need to pass more than 1 argument to the function
  wrap them in an object.` Write the measurement function to destructure a
  single object from the start.
- **After a pin bump, clear the dev server's dependency cache.** Vite
  pre-bundles dependencies; restarting alone can serve the *previous* library
  build, and you will happily "prove" that nothing changed. `rm -rf
  node_modules/.vite` and restart before measuring.
- **When a difference has no technical cause, do not decide it alone.** Bring
  the design owner two screenshots of the same element, the two measured values,
  and the options with their costs. That is an arbitration, not a bug.

### What this step structurally cannot find

**A check that compares against a reference is blind to defects present in the
reference.** If the library's documentation site has the same problem, the diff
is clean and the defect ships.

That is not hypothetical: the collapse control had no pointer cursor, in the
product *and* on the documentation site, so every computed-style comparison
agreed perfectly. It was found by a human moving a mouse, in product review.

Worse, the whole method is biased towards *static* properties. Computed styles
are read on an element at rest. Anything that only exists during an interaction
— `:hover`, `cursor`, `:focus-visible` rings, keyboard traversal, the state of a
control while it is pressed — is not in the comparison at all.

**The counter-measure: the visual verification must include an interaction
pass**, not only a look. On every interactive element the component renders:

- hover it and check the **cursor** shape, and any hover styling;
- reach it with `Tab` alone and check the focus ring is visible **on the
  keyboard path**, in both themes;
- activate it with `Enter` and with `Space`, and check both do what the mouse
  does;
- for anything that opens: check it closes on `Escape` and that focus returns
  somewhere sensible.

Do this in the running product, in both themes and both rail states. It takes a
few minutes and it catches the class of defect no diff can.

---

## Step 6 — Bridge the theme

**Purpose.** Make the library resolve its light/dark tokens.

The library reads a `.light` / `.dark` class. It must be on
`document.documentElement`.

In the product's theme provider, alongside whatever it already sets:

```ts
document.body.setAttribute('data-theme', themeToSet);
document.documentElement.classList.remove('light', 'dark');
document.documentElement.classList.add(themeToSet === 'light' ? 'light' : 'dark');
```

**Success criterion.** Switch the product theme and confirm the library
component follows, **including its tooltips and dropdowns**.

**Traps.**

- **Do not put the class on a container.** The library portals tooltips,
  submenu flyouts and dropdown content directly into `<body>`. A scoped class
  leaves every floating layer unthemed — and you will only notice it on hover.
- **Find every writer before you add one.** `grep -rn "data-theme" src/` —
  products often set the theme attribute from more than one place, and the
  snippet above assumes a single writer of a literal `light`/`dark`. Put the
  root-class write in the one resolver they all share; if there is none, make
  one.
- **Check how the product resolves a theme *name*.** If it branches on
  `theme.name === 'Light'`, then every custom theme name stored in the database
  falls silently into the dark branch.
- If the product already has a *scoped* theme-class helper and it is the only
  theme-class mechanism it has, **amend it — and its doc comment — rather than
  adding a second, competing writer.** Two writers of the same class is the
  failure this trap is really about.
- Map defensively: anything that is not `'light'` should resolve to `'dark'`,
  so an unexpected theme name does not leave the app unstyled.

---

## Step 6b — Audit the product's custom theme

**Purpose.** Find out, *before writing any adapter code*, which colours the
component you are replacing takes from a **user-customisable** theme. Adopting
library tokens for those is not a visual delta — it is a functional loss for
every customer who set them.

Many products let an administrator store a background colour, an accent, a
logo. The legacy component reads them at runtime. The library component reads
its own tokens. Nothing warns you: the result looks right, on your instance,
with default settings.

**Procedure.**

1. **Enumerate.** For every colour the legacy component paints with, decide
   whether it is hardcoded or settings-driven. Follow it back to its source —
   a theme object built from a database record, not a constant file.
2. **For each customisable colour, choose one of three, explicitly:**
   - *preserve by inline style* — works when the library component spreads
     props onto its root element and you can pass the value through;
   - *preserve by overriding the library custom property* — for values the
     component reads from a token;
   - *accept the loss* — legitimate, but **escalate to the sponsor before
     implementing it**, never after. It is a product decision, not yours.
3. **Record the decision** where the next reader will find it: an inline
   comment at the override, and a line in `LIBRARY-FEEDBACK.md` if the library
   offers no way to honour the value.

### The derived-token trap

This one costs a checkpoint round trip, and it is invisible outside a browser.

Overriding a library custom property through the cascade works for that
property. It does **not** reach the tokens *derived* from it. Derived tokens are
declared like this, in the library's own stylesheet:

```css
:root, :host, .light { --nav-item-bg-selected: color-mix(in oklab, var(--brand-primary) 12%, transparent); }
```

`color-mix()` is substituted **where the declaration lives** — at `:root`. An
override you apply on a subtree changes `--brand-primary` for that subtree only,
long after the derived value has been computed from the original. The symptom is
partial and looks like a styling bug: on OpenCTI the selected row's left border
followed the customer's accent while the row's background tint stayed Filigran
blue.

**So: when you override a library custom property, find every token derived from
it and override each one too, with the library's own formula.**

```bash
grep -o -- "--<token>[a-z0-9-]*:[^;}]*" \
  node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.css \
  | sort -u
```

**Success criterion.** Set a non-default custom colour in the product's own
settings, reload, and confirm in the browser that **every** surface that used to
follow it still does. Compare the two *sets* of tokens — the ones derived from
the base token, and the ones you overrode — and check they are equal. A
substring check ("the accent appears in the stylesheet") passes while three
derived tokens are still wrong.

**Trap.** Doing this audit after the adapter is written means re-opening code
you had already validated, and re-running the whole visual checkpoint. It is
half an hour before, and half a day after.

---

## Step 7 — Read the component's real API before designing anything

**Purpose.** Avoid designing an integration around props that do not exist.

Do not work from the docs site alone. **The fastest and most reliable source is
the build you already installed** — it is, by construction, exactly your pinned
code, with no clone and no build step:

```bash
cd <product-front-directory>/node_modules/@filigran/design-system/packages/filigran-design-system/dist
grep -n "interface NavbarSubmenuProps" -A 40 index.d.ts   # the typed contract, with its doc comments
grep -n "var NavbarSubmenu" -A 80 index.mjs               # what it actually renders
```

`index.d.ts` carries the full prop documentation (the library writes long,
useful comments, including arbitration history), and `index.mjs` is readable
enough to answer questions the types cannot: which element is rendered in which
state, which Radix primitive is underneath, what is portalled, which classes
carry the visuals. Every non-obvious behaviour this pilot relied on was found
that way, in minutes.

Clone the repository as well only when you need its tests, its RFCs or its
history:

```bash
git clone https://github.com/XTM-Foundation/filigran-design-system.git /tmp/fds
git -C /tmp/fds checkout <PIN_SHA>
ls /tmp/fds/packages/filigran-design-system/src/components/
```

Write down, per component: required props, which props are ignored in which
mode, how selection/active state is expressed, and what it renders in each
state.

**Traps found in the pilot, all of which changed the design:**

- **`asChild` disables sibling props.** On `NavbarItem` and `NavbarSubmenuItem`,
  `asChild` makes `icon`, `showIcon` and `chevron` no-ops — Radix's `Slot`
  cannot inject wrappers into an arbitrary child. If you need `asChild` (you do,
  for real links), you must hand-compose the row internals *and* copy the
  library's internal class names. Isolate that in one file so the debt is
  visible.
- **State can be expressed through the DOM, not through props.** `NavbarItem`
  has no `selected` prop: it derives selection from a native
  `aria-current="page"` on the child. Grepping for a `selected`/`active` prop
  and finding nothing does not mean the feature is missing.
- **Some props only apply in one mode.** `NavbarSubmenu`'s `href` / `to` are
  applied *only while collapsed*.
- **Slots have structural order you cannot override.** The navigation's collapse
  toggle is always rendered last, below the `footer` slot. If your legacy layout
  had something below it, that order changes. Accept it and document it rather
  than fighting the component.
- **Required-in-context props.** `tooltipLabel` is mandatory on a collapsed
  `asChild` row without a chevron; omit it and the tooltip is silently skipped
  (with a development-only warning).

---

## Step 8 — Build the product adapter

**Purpose.** Keep the library component generic and the product logic in the
product.

Structure that worked (`src/components/common/menu/navbar/`):

| File | Responsibility |
|---|---|
| `nav-menu-model.ts` | The product's menu data types. No library import. |
| `useNavbarState.ts` | Collapse state: persistence, defaults, cross-component broadcast. Exports a **pure resolver**. |
| `NavbarRowContent.tsx` | The row internals `asChild` forces the product to own. Isolated so the debt is countable. |
| `AppNavbar.tsx` | Maps the product model onto the library component. |

Non-negotiables:

- **Preserve permissions exactly.** Filter on the same rights, at the same
  granularity (groups *and* items *and* sub-items), and add a test per level.
- **Preserve persistence exactly.** Keep the same storage key and the same
  broadcast, and make **every** reader go through one shared resolver. In the
  pilot the top bar independently read `localStorage.getItem('navOpen') ===
  'true'`; the moment the default became viewport-dependent, that copy
  disagreed with the navigation on first mount and offset the top bar by the
  wrong width. Export the resolver and import it in both places:
  ```ts
  export const resolveInitialNavOpen = (stored: string | null, viewportWidth: number): boolean => {
    if (stored === 'true') return true;
    if (stored === 'false') return false;
    return viewportWidth >= 1024;
  };
  ```
- **Keep side effects out of render.** Legacy code sometimes calls setters
  during render. When you recompose, move them into the event handler.
- **Never hardcode a token value** to approximate a library style that does not
  exist yet. Use the closest existing token, mark it provisional in a comment,
  and adopt the real one at the pin bump.
- **Update every width constant the rest of the shell depends on.** The library
  rails are 180px expanded and 48px collapsed — the legacy value was probably
  something else (55px here).
- **Keep the state broadcast pure.** Do not fire a side effect inside a
  `setState` updater; React double-invokes updaters in StrictMode.

### Ask what the legacy handler *does*, not what it looks like

The most valuable thing this pilot got wrong, twice, is worth its own habit.

OpenAEV's tenant switcher was a MUI popover whose items ran an `onClick`, so it
was filed as "an action menu — the library has no primitive for it" and kept in
MUI. Reading the handler two levels down told a different story: it assigned
`window.location.href`. Every tenant has a URL. It was a **navigation menu
wearing a disguise**, and it recomposed onto `NavbarSubmenu` with real `<a
href>` rows — which additionally bought ⌘/Ctrl-click "open in a new tab", a
capability the popover never had.

So: before writing "the library cannot do this", follow the handler to the
bottom. `location.href = …`, `navigate(…)`, `router.push(…)` and a `<form
action>` are all links in disguise. Only a handler that mutates state without
changing the address is really an action.

Two forks you will meet on that road, both arbitrated here:

- **A selector is not a page group.** `NavbarSubmenu` copies a child's
  `aria-current="page"` onto its trigger row. Correct for a group of pages;
  wrong for a selector, where the current item is *always* in the list — the
  trigger would stay lit forever. Mark the current entry another way (this
  pilot uses a `check` icon plus an `aria-label`) and keep `aria-current` for
  real page state.
- **Gating a submenu by intercepting its opening backfires.** While collapsed,
  the library opens the flyout on hover, so an `onOpenChange` guard fires on a
  mouse-over — here it would have thrown an upsell dialog at anyone brushing
  past. Branch the *rendering* instead: gated state renders a plain
  `NavbarItem` button, granted state renders the submenu.

### Keeping the product's own icons is a legitimate choice

The library's icon slots take `ReactNode` **by design**: a host may pass the
library's `Icon` or its own glyphs. This pilot kept OpenAEV's existing MUI
icons, so the navigation renders filled glyphs where the documentation site
renders lucide outlines. Same 16px box, same colour token, no layout
consequence — the pilot's principle is iso-functionality, and nothing is lost.

Decide this explicitly rather than by accident, and **write the decision down**
(here, `LIBRARY-FEEDBACK.md` entry 10) — otherwise the next reviewer diffing
against the documentation site will re-open it as a bug, as happened here.

Converging a product onto the library's lucide set is its own design mission:
every menu entry has to be mapped and arbitrated one by one, and some will have
no equivalent in the library's registry. That is not integration work, and it
does not belong in a migration pull request.

---

## Step 9 — Delete the code you replaced

**Purpose.** A migration that leaves the old implementation behind has not
migrated anything.

```bash
git rm -r <legacy-folder>
grep -rn "LegacyThing\|legacy-folder-name" src/ tests_e2e/
```

**Success criterion.** The grep returns nothing, and type-check passes.

**Traps.**

- **Type-only imports keep dead folders alive.** The legacy folder here was
  referenced only by a `import { type … }`. Move the model into the new folder
  and retarget the import.
- **Check comments too.** Stale comments referring to deleted files are the
  next reader's wild goose chase.
- Do not delete the legacy code "later, in a follow-up". Later never comes, and
  the two implementations drift.

---

## Step 10 — Tests

**Purpose.** Prove the behaviours you promised to preserve.

Test at minimum:

1. **The pure state resolver** — every branch, including a corrupted stored
   value.
2. **Permission filtering** — a hidden item is absent, a visible one present, at
   every nesting level.
3. **Rows are real links** — assert `href`. This is what makes Ctrl/Cmd-click
   and "open in new tab" work, and it is exactly what silently regresses when
   someone later simplifies the `asChild` wiring away.
4. **Persistence** — toggling writes the expected storage key.

```bash
npx vitest run <your-test-folder>
```

**Traps.**

- **Check which matchers the product actually has.** `toBeInTheDocument` /
  `toHaveAttribute` come from `jest-dom`; if the product does not register it,
  they fail with a cryptic `Invalid Chai property`. Use
  `expect(el).toBeTruthy()` and `expect(el.getAttribute('href')).toBe(…)`.
- **End-to-end selectors will break, and that is your change.** Library rows are
  `<a>` and `<button>` elements, so any `getByRole('menuitem', …)` from the MUI
  era stops matching. Update the page objects in the same pull request, and say
  so in the description — an introduced break that you fixed is not the same
  thing as a pre-existing one.
- **You cannot always add a `data-testid` to a library component.** Several
  library components type their props explicitly and do **not** spread the rest
  onto the DOM node, so `<NavbarSubmenu data-testid="…">` type-errors and, even
  cast, renders nothing. Re-anchor the page object on what the component does
  emit — a stable class on your own wrapper (`.app-navbar`), the element role,
  or the visible label — and keep testids on the elements you render yourself
  (`NavbarSubmenuItem asChild` children accept them, because you own that node).
  Rewrite the page object's internals while preserving its public method
  signatures: the calling specs then need no edit at all. Prove the new locators
  before pushing by replaying them with a throwaway Playwright script against
  the running product, in **both** rail states — a collapsed rail swaps an
  accordion for a floating menu, and a selector that works expanded can miss
  collapsed.
- **New user-visible strings are a CI gate of their own.** Rewriting the
  navigation introduces labels the product never had — an `aria-label` on the
  rail, a tooltip on a footer row. OpenAEV's `yarn i18n-checker` fails the
  `Frontend Quality` job when a `t('…')` key is missing from **any** of the nine
  `openaev-front/src/utils/lang/*.json` files, and it only reports the key name,
  not the file that uses it. Run `yarn i18n-checker` locally before pushing, and
  add each new key to all nine files. The files are sorted case-insensitively,
  so insert in place rather than appending — re-serialising the JSON reorders
  hundreds of untouched lines and drowns the diff.
- **A red check masks every check behind it.** While the library install was
  failing to authenticate, no other quality gate in that job could even run — so
  "everything else is green" meant nothing. The moment the token was fixed, an
  untouched i18n failure surfaced that had been there all along. Expect one more
  round of unknown reds after you fix a blocking one, and do not promise a green
  pipeline until you have seen every job actually execute.
- **Read a running job's logs without waiting for it.** `gh run view --log`
  refuses while a run is in progress; the API does not:
  ```bash
  gh run view <run-id> --json jobs -q '.jobs[] | select(.conclusion != "success") | (.databaseId|tostring) + " " + .name'
  gh api repos/<owner>/<repo>/actions/jobs/<job-id>/logs
  ```
- **Date every failure.** Before touching a red check, establish whether it was
  already red on the target branch. In this pilot, two `TS2300` duplicate-identifier
  errors in an unrelated AI dialog file and one stale-token-bridge conformity
  warning were pre-existing; they belong in the report, not in the pull request.

---

## Step 11 — File what the library is missing

**Purpose.** The product never forks, patches or approximates the library.

Anything the library cannot do goes into `fds-migration/LIBRARY-FEEDBACK.md`,
in English, with: what the product needed, what the library offers today, what
the product did instead, and the concrete ask. Reference the file from the code
comment that carries the workaround, so the two never drift apart.

**Trap.** Resist "I'll just tweak the library, it's two lines." A product-side
pull request against the library is out of contract, and the fix will not be
designed, reviewed or tokenised.

---

## Step 12 — Run the product for the visual checkpoint

**Purpose.** Every claim in this playbook ends in a browser. You will need the
product actually running — with a backend, logged in, on real data — several
times: to measure (Step 5b), to verify each compensation removal, and to hand a
URL to whoever reviews the design.

Nothing here is design-system work; it is the step that most reliably eats an
afternoon, so budget it early rather than discovering it at checkpoint time.

**The recipe that works for OpenAEV** (transpose, do not copy blindly):

```bash
# Backend — needs JDK 21 and the datastores; it listens on :8080
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew bootRun            # from the repository root

# Front — Vite dev server, proxied to the backend
cd openaev-front && npx vite --port 3010
```

Log in with the development credentials your product seeds (`admin@openaev.io` /
`admin` here).

**Success criterion.** `curl -o /dev/null -w '%{http_code}' http://localhost:3010`
returns 200, and `…:8080/api/me` returns 401 rather than a connection error — a
401 means the backend is up and simply does not know you yet.

**Traps.**

- **The local configuration file is deliberately not in the repository.**
  OpenAEV's `application-dev.properties` is git-ignored; the backend will not
  start without it. Ask a team member for theirs rather than reconstructing it
  from the code — it also carries the datastore ports (OpenSearch on a
  non-default port here), which you will otherwise chase for an hour.
- **You do not need the whole stack.** For a navigation change, a backend that
  authenticates and serves the menu's data is enough; do not spend time making
  every collector, injector or worker healthy.
- **Keep it running.** Restarting the backend costs minutes; the front reloads
  on its own. The exception is a pin bump — then, and only then, restart the
  front *and* clear `node_modules/.vite`.
- **Never measure a state you have not proved you are in.** Half of this
  pilot's browser harnesses silently measured the *expanded* rail while
  reporting on the collapsed one, because they hunted the collapse control by
  `aria-label` — and the library's toggle has none, only the visible text
  "Collapse". A `find()` that returns `undefined` no-ops, the script carries on,
  and you conclude a fix does not work. Assert the state first
  (`expect(rail).toHaveWidth(48)`, or simply log it) and only then measure.

### Showing a state your instance does not have

The reviewer's own instance is usually the *simplest* one: single tenant, no
Enterprise licence, one product. The feature you have just rebuilt may therefore
be invisible to the person who has to approve it — this pilot's tenant switcher
only renders for users with more than one tenant.

Build a **demo scaffolding**: a few lines that fabricate the state behind a
`localStorage` flag, right where the real data enters the component.

```ts
// NOT COMMITTED — demo scaffolding for the design checkpoint.
const demo = localStorage.getItem('__demoTenants');
const tenants = demo ? DEMO_TENANTS : (userTenants ?? []);
```

Then keep it **out of the commit** and alive in the working tree at the same
time. The discipline that worked, once per amend:

```bash
git diff > ~/demo-scaffolding.patch     # once, when you write it
git apply -R ~/demo-scaffolding.patch   # before staging anything
grep -rn "__demoTenants" src/ | wc -l   # must print 0
git commit --amend --no-edit && git push --force-with-lease
git apply ~/demo-scaffolding.patch      # put it back for the reviewer
```

**Traps.**

- **`git add -A` will happily commit your scaffolding.** The reverse-apply above
  is not optional ceremony; run the `grep` every single time, it is the only
  thing standing between you and a demo constant in production.
- **Regenerate the patch whenever the file underneath changes**, or the
  re-apply fails and you will be tempted to fix it by hand at the worst moment.
- **Tell the reviewer the toggle**, in plain words: which key, which values,
  what each one shows, and how to get back to normal.

---

## Final verification checklist

Run all of these before requesting review.

| # | Check | Command / how |
|---|---|---|
| 1 | Types | `yarn check-ts` — compare against the target branch to date pre-existing errors |
| 2 | Lint | `npx eslint <changed paths>` (`--fix` handles import ordering) |
| 3 | Unit tests | `npx vitest run <your test folder>` |
| 4 | New user-visible strings | `yarn i18n-checker` — every new `t('…')` key must exist in **all** the product's language files, or the `Frontend Quality` job fails |
| 5 | Production build | `yarn build` — catches resolution and stylesheet problems the dev server hides |
| 6 | Migration conformity | `node fds-migration/scripts/check-fds-conformity.mjs` |
| 7 | No dead legacy code | `grep -rn "<legacy names>" src/ tests_e2e/` returns nothing — **comments included**, a comment naming a file you deleted is a false statement |
| 8 | CI green | Every check on the pull request, **including the install job** — that job is the proof the whole method works |
| 9 | Computed-style diff vs. the documentation site | [Step 5b](#step-5b--diff-against-the-librarys-own-documentation-site) — every measured value identical, or a named cause |
| 10 | Visual checkpoint | [Step 12](#step-12--run-the-product-for-the-visual-checkpoint) — run the product and verify by hand (list below) |
| 11 | **Your row is in the pilot index** | [Step 0.5](#step-05--read-the-previous-pilots-implementation) — product, repository, branch, **implementation directory**, feedback file. A pilot missing from that table does not exist for the next one, and nothing else in this list fails when you forget it |
| 12 | CI secret guard | The [guard test](#then-stop-enumerating-by-hand--ship-the-guard-test) is in the product's own suite and green — not merely copied into a folder the runner never collects |
| 13 | Custom-theme non-regression | [Step 6b](#step-6b--audit-the-products-custom-theme) — with a non-default custom colour set, every derived token still follows it (compare the two **sets**, not a substring) |

**Visual checkpoint — what to look at, in both light and dark themes:**

- expanded and collapsed rails, and the transition between them;
- collapsed rows: tooltips appear, submenus open as flyouts;
- Ctrl/Cmd-click and middle-click a row: it opens in a new tab;
- the active row is highlighted, and only it;
- every library `<button>` is transparent, not grey (the Step 5 reset);
- separators are a single 1px rule, not a 2px box (the Step 5 `<hr>` reset);
- rows keep their designed height with the product's **full** menu loaded, at a
  short viewport — if they shrink, the list should scroll instead;
- theme switching, **with a tooltip and a dropdown open** (portalled content is
  where the theme bridge fails);
- a narrow viewport (< 1024px): the navigation starts collapsed, and the toggle
  still works;
- the state survives a reload.

---

## The checkpoint loop — a review that changes its mind is the process working

**Purpose.** Set the right expectation: the visual checkpoint is not a
formality at the end. It is a **loop**, and it is where the design decisions are
actually made.

This pilot went through four rounds after "it works": a repositioned menu entry;
a tenant switcher rebuilt from a floating menu onto an in-rail submenu; then the
same switcher reverted towards a floating menu again; and, along the way, a
systematic style diff that the reviewer asked for rather than listing the
differences by hand.

Two things to take from that.

**A reversal is cheaper than a regret.** The third round undid the second one.
That is not waste: the in-rail version had to exist for anyone to see that the
floating menu read better in that particular rail. Two iterations before merge
cost hours; a frozen choice everybody regrets costs a follow-up migration. Say
what you built, show it running, and let the decision be made on the artefact
rather than on a description of it.

**Come back with evidence, not opinions.** Every round in this pilot was settled
by something measurable: computed styles side by side, a DOM fact from the
library's build, a live measurement in both rail states. "It looks close enough"
never resolved anything; `36px vs 32px` always did.

### When a component nearly fits: evolve the library, do not duplicate it

The decision you will face at least once: the library has a component that is
*almost* what you need. The temptation is to reproduce it product-side — its
class names are in the shipped stylesheet, so a copy renders correctly today.

**Resist it, and make the case with facts.** How this pilot argued it, the last
time it came up:

1. **Establish precisely what does not fit, from the build, not from memory.**
   Here: `ProductSwitcher`'s option rows render their content in a fixed,
   hard-clipped `w-[100px]` box with no text mode (the label is `sr-only`);
   there is no current-item indication; the trigger is not composable (fixed
   logo slot and a fixed chevron, no room for a product row's icon, label or
   badge); and there is no controlled `open`/`onOpenChange`, so a permission
   gate has nothing to hook onto.
2. **Say what a product-side copy would really cost.** Not the first day — the
   later ones. A copy freezes the library's *current* internals: this pilot's
   own pin bump changed rail density (separators, gaps, submenu group
   backgrounds) and typography, and every copied panel would have silently
   stayed behind. That debt is invisible, permanent, and grows at every bump.
3. **Write the ask as a component, not as a patch to your case.** "Export the
   menu shell (`DropdownMenu`, `…Trigger`, `…Content`, `…Item` with `asChild`,
   `…Separator`) and let `ProductSwitcher` consume it" serves every product and
   keeps one source of truth. "Add a `tenantMode` prop to `ProductSwitcher`"
   serves one caller and makes the component about someone else's domain.
4. **Then stop and ask.** The decision "library evolution or product
   compensation" is not the integrator's to take alone — it changes the
   library's roadmap. Present the two paths with their costs and let the design
   owner choose.

The outcome here was the first path: the library took a real `Menu` component,
designed in Figma, which `ProductSwitcher` will consume — so the product's menu
is identical *by construction* rather than by imitation. The product-side work
waited for that merge, with the existing implementation staying in place and
green in the meantime; when the component landed, the rewrite took **1 min
21 s** and measured identical to the documentation site with no product CSS at
all. The waiting was the cheap part.

**And while you wait, do not half-build it.** Leaving a working implementation
alone is a legitimate state. Note it, keep the branch green, and do the parts
that do not depend on the library.

---

## The pin-bump exercise

Your pin is a snapshot. Library pull requests merge after it, and adopting them
is a routine chore. Do it deliberately, on its own branch, as its own
single-subject pull request.

### The heart of the exercise: compensation → removal

Almost everything temporary you write during an integration is a **compensation**
for something the library does not do *yet*. A compensation is legitimate — a
product cannot wait — but it is only legitimate if it carries three things:

1. a comment saying **why** it exists,
2. an entry in `LIBRARY-FEEDBACK.md` so the gap is actually fixed upstream,
3. an explicit, testable **removal condition**.

A pin bump is where those conditions get tested and the compensations get
deleted. If a bump adds capability but leaves your compensations in place, the
debt is permanent and the next bump is harder. Treat "what did I just delete?"
as the primary success measure of a bump, ahead of "what did I gain?".

This pilot shipped six compensations, all with removal conditions. **All six
were removed or adopted inside the pilot itself**, across three bumps, which
makes them the worked examples of the whole exercise. Note the last one: a
compensation is not always CSS — it can be a whole structural detour.

| Compensation shipped by this pilot | Where | Removal condition | State |
|---|---|---|---|
| Collapsed logo forced to fill and centre inside the switcher's fixed 126px slot | `LeftBarHeader.tsx` | The `logoCollapsed` prop lands (feedback entry 4). | ✅ **removed** at pin `3442003a` |
| `button { … }` reset on the host | `openaev-front/src/static/css/design-system-host.css` | The library's self-defensive styles land. | ✅ **removed** at pin `d7ea4f2` |
| `hr[class*="border-t"] { … }` reset on the host | same file | Same — the self-defensive styles must cover `<hr>`, not just `<button>`. | ✅ **removed** at pin `d7ea4f2` |
| `.app-navbar .overflow-y-auto > * { flex-shrink: 0 }` | same file | The library marks its scroll list's children non-shrinking (feedback entry 9). | ✅ **removed** at pin `d7ea4f2` |
| Provisional `severity="neutral"` on the Enterprise-Edition chip | `LeftBarTenantSwitcher.tsx` | The `Chip` Enterprise-Edition/tonic tone lands. | ✅ **adopted** at pin `d7ea4f2` (`tone="tonic"`) |
| **Structural**: the tenant menu built on `NavbarSubmenu`, a *navigation* primitive used as a selector because no menu primitive existed | `LeftBarTenantSwitcher.tsx` | A real `Menu` component lands (feedback entry 3). | ✅ **removed** at pin `9cd4271` — now `Menu` + `MenuTrigger asChild` + `MenuItem asChild` anchors |
| `body > [data-radix-popper-content-wrapper] { z-index: 1300 !important }` | `openaev-front/src/static/css/design-system-host.css` | The library exposes a stacking hook — the ask is a `--fds-z-overlay` custom property consumed by all seven portalled surfaces (feedback entry 12). | ⏳ **open** — one rule for one cause; do not add a second |

What is left in `design-system-host.css` after the second bump is **one rule**,
and it is not a compensation at all — it sizes the *product's own* MUI icons to
the 16px the library's rows are designed around. That is the healthy end state:
the host file holds host concerns, not library debt.

#### Worked example — "what did I just delete?"

The pilot pinned before the `ProductSwitcher` evolution existed. At that pin the
logo slot was a fixed 126px box **in both rail states**, and it was centred on
the 48px collapsed rail — so the collapsed logo hung 39px off the left edge,
entirely outside the viewport. The product compensated: one `<img>` swapping its
own source on the collapse state, forced to `width: 100%` and re-centred with
`objectPosition`.

That compensation carried its three obligations: a comment naming the 126px
slot, feedback entry 4, and a removal condition — *the `logoCollapsed` prop
lands*. The condition came true. What the removal actually looked like:

```diff
-      // The library slot is a fixed 126px-wide box in BOTH rail states, and it
-      // is centred on the 48px collapsed rail — so it hangs 39px off the left
-      // edge. A collapsed logo must therefore fill the slot and centre its own
-      // content, otherwise it renders outside the viewport.
-      logo={<img src={navOpen ? theme.logo : theme.logo_collapsed} … 
-              style={{ width: '100%', objectPosition: navOpen ? 'left center' : 'center' }} />}
+      logo={<img src={theme.logo} className="h-7 w-full object-contain object-left" />}
+      logoCollapsed={<img src={theme.logo_collapsed} className="h-7 w-7 object-contain" />}
+      logoHref={`${computeTenantBasename()}/admin`}
+      logoLabel={t('Home')}
```

Three things went away with it, and this is the point: the **conditional
source**, the **forced width**, and the **`objectPosition` correction** — plus
the component's `navOpen` prop, which existed only to feed that conditional and
whose removal rippled out to the call site. Compensations are rarely one line;
they grow tendrils into signatures.

**Two lessons the removal itself taught, both worth expecting:**

- **A bump can add capability *and* new obligations.** `logoHref`/`logoTo`
  render a **plain anchor with no router integration**. This product's router
  runs under a tenant-prefixed basename, so a bare `/admin` would have left the
  tenant. The adoption had to prefix the basename by hand. Read the new props'
  documentation properly; do not assume "link" means "router link".
- **Verify the removal in the browser, not in the diff.** Deleting the
  compensation was not enough: the library's slots clip their child rather than
  size it, so the raw 350×346 asset rendered at natural size inside a 28×28
  slot — a crop of its middle. Measured, fixed by sizing the asset, re-measured:
  collapsed logo 28×28 at x=10 inside a 48px rail (centred), expanded 126×28 at
  x=16. **A compensation is removed when the pixels are right, not when the code
  is gone.**

#### Second worked example — four compensations removed in one bump

The pilot's second bump took the pin from `3442003a` to `d7ea4f2`, four library
changes later. Three of them closed a compensation each, and a fourth replaced a
placeholder. Every removal was verified by measurement, not by reading the diff:

| Removed | Verification that it was safe to remove | Measured result |
|---|---|---|
| `button { background-color: transparent; border: 0; … }` | Delete, reload, read the collapse toggle's computed `background-color` | `rgba(0, 0, 0, 0)` — the library is now self-defensive |
| `hr[class*="border-t"] { … }` | Delete, read a separator's computed `border-width` and height | `1px 0px 0px`, height 1px |
| `.app-navbar .overflow-y-auto > * { flex-shrink: 0 }` | Delete, load the **worst case** — 17 entries at a 1000px viewport — and read a row's height | 36px, list scrolling (was 32px before the library fix) |
| `severity="neutral"` → `tone="tonic"` | Read the chip's computed `background-color` | `color(srgb 0 0.941 0.737 / 0.2)` — the brand turquoise, from the token, never hardcoded |

**The reflex to build: reproduce the original symptom's worst case.** The
`flex-shrink` guard is the example — removing it and looking at a normal page
proves nothing, because the defect only appeared when the list overflowed. Keep
the reproduction recipe *in the compensation's comment*, so the person doing the
bump can run it without rediscovering it.

**What arrived without anything to remove.** The same bump carried a typography
correction at the token generator level. Nothing to delete, but it moves
rendered text: navigation labels went from the browser's rounded
`14.4px / 21.6px / normal` inheritance to the library's own
`14px / 21px / 0.105px letter-spacing`, and the rail's rhythm tightened
(separators at 4px margins, the inter-item gap removed, submenu groups on an
elevation background). **Always re-take the visual checkpoint after a bump**,
even when your own diff is empty — the pixels moved, and someone must look.

### Worked example — a bump that brings a brand-new component

The second measured bump of the pilot, and a different shape from the first: it
carried **no fix and no compensation to remove**, only one new component
(`Menu`) that closed a product report filed months earlier. The product could
then delete its own workaround — not a CSS compensation this time, but a
*structural* one: the tenant switcher had been rebuilt on `NavbarSubmenu`, a
navigation primitive, because no menu primitive existed.

| Step | Time |
|---|---|
| Fetch, read what the bump contains | ~1 min |
| Re-pin + `yarn install` | **15 s** |
| Read the new component's real API from the installed `dist` (`index.d.ts`, then `index.mjs` for the parts the types cannot express) | **45 s** |
| Rewrite the product component onto it (type-check + lint clean) | **1 min 21 s** |
| Restart the dev server with a cleared cache, re-apply the demo scaffolding, verify in the browser: anchors, current item, both themes, both rail states, the permission gate, the keyboard | ~6 min |
| Re-anchor the end-to-end page object and replay its exact locators live | ~3 min |
| Update the unit tests, date the failures that were already red | ~4 min |
| Full local validation + production build | ~1 min |
| Rebuild the reference documentation site at the new pin | **41 s** |
| Focused computed-style diff of the new component, both themes | ~2 min |
| **Total, hands on keyboard** | **≈ 20 min** |

**What that bump changed in the product**, and the lesson worth keeping: the
panel is now the library's, so it is identical to the documentation site by
construction — panel `rgb(19,33,62)` dark / `rgb(228,229,231)` light, radius
4px, `min-width 200px`, `max-width 300px`; rows `min-height 32px`, padding
`0 16px`, gap 8px, `12px/18px IBM Plex Sans`, letter-spacing `0.09px`. **Zero
measured difference** against the reference, on the first try, with no product
CSS at all. That is the whole argument for waiting for a library primitive
instead of reproducing one: the imitation would have needed its own diff, its
own maintenance, and its own re-measure at every future bump.

**And a structural compensation is worth removing even when nothing is broken.**
The `NavbarSubmenu` version worked and was green. It was still a navigation
component pressed into a selection role; the replacement is semantically honest
(`aria-current` on the selected row, scoped to the panel), it keeps what the
detour had bought (real `<a href>` rows, so ⌘/Ctrl-click still opens a tenant in
a new tab), and it deletes the product-side reasoning that existed only to work
around the gap. Re-read your own "documented divergences" at every bump: some of
them have quietly become removable.

### Worked example — the bump you cannot do

**The most useful of the three, because it is the one nobody plans for.** The
third measured bump of the pilot was meant to be the easy shape: one library
fix, one visual change, nothing to remove — the everyday case. It never got
past `yarn install`.

| Step | Time |
|---|---|
| Fetch the library, read the new SHA and the fix's diff | ~1 min |
| **Measure the "before" state in the running product** (the value the fix is supposed to move) | ~30 s |
| Re-pin + `yarn install` → **fails** | **10 s** |
| Read the packing log, identify the failing module | ~30 s |
| Establish the cause: compare both lockfiles across three commits | ~1 min |
| Bisect empirically — install at the suspected commit to prove it is the first broken one | ~1 min |
| Restore the previous pin, verify the product is healthy again | ~30 s |
| **Total to a proven diagnosis and a safe tree** | **≈ 2 min 20 s** |

What failed, and why it matters to you: the library's repository carries **two**
lockfiles. Its own pipeline is entirely pnpm, so `pnpm-lock.yaml` is always
fresh — but a git dependency installed by Yarn Berry bootstraps from
`yarn.lock`, then runs `prepack` to build `dist`. A PR had added two runtime
dependencies, updated `package.json` and `pnpm-lock.yaml`, and left `yarn.lock`
stale. The library's CI was green. Every consumer was broken:

```
➤ YN0036: Calling the "prepack" lifecycle script
    STDERR …/Checkbox.tsx(2,36): error TS2307: Cannot find module
           '@radix-ui/react-checkbox'
➤ YN0058: Packing the package failed (exit code 1)
```

**The lessons, in order of how much time they save you:**

- **A green library CI does not mean the library is installable.** If nothing in
  its pipeline installs it the way you install it, that path is untested. Before
  a bump, this is the *first* thing worth knowing — check whether the library
  has a consumer-install job, and if it does not, expect this eventually.
- **Read the packing log, not the yarn summary.** `YN0058: Packing the package
  failed` says nothing. The real error is in the `pack.log` whose path it
  prints, and it is usually one line naming a module.
- **Bisect, do not deduce.** The failing file made the culprit obvious, and the
  obvious answer was still worth one minute of proof: install at the suspected
  commit and at the one before it. You are going to file a report that makes
  someone else drop what they are doing — bring a bisected fact, not a theory.
- **Restore the previous pin immediately, then write.** A broken `package.json`
  in the tree while you compose a report is how a red branch happens. Revert,
  re-install, confirm the product still serves, *then* think.
- **Do not compensate an install failure.** This is not a styling difference to
  work around — there is no product-side version of "the package will not
  build". The pin stays where it was, the fix you wanted stays unreceived, and
  the report is the entire deliverable.
- **Measure the "before" state anyway.** It costs 30 seconds and it survives the
  failure: when the bump finally happens, you already hold the baseline the fix
  is supposed to move.

Filed as feedback entry 14, with the ask in two parts — regenerate the lockfile
to unblock today, and add a consumer-install job so the next occurrence fails on
the library's own branch instead of in a product weeks later.

#### How that story ended

**Both halves of the ask shipped, and the diagnosis was corrected on the way.**
The library's fix (#79) landed the same day. What it revealed is the part worth
carrying:

- **The real cause was one manifest wider than this product could see.** No
  package manager can target a subdirectory of a git dependency, so all of them
  install the repository *root* and run its build against the **root**
  manifest. The offending PR had declared its dependencies only in the package's
  own manifest. So npm and pnpm broke identically — it was never a Yarn
  problem. This product only saw Yarn because Yarn is what it uses.
  **The lesson: reproduce on a second package manager before naming a cause.**
  A product observes the failure through its own toolchain and will name that
  toolchain. Being one manifest too narrow cost nothing here because the
  bisection was right, but a narrower report gets a narrower fix.
- **The guard is now blocking by construction.** An install proof from a blank
  external project runs as a *step of the required job* — not as a separate
  optional check that someone must remember to mark required — plus a parity
  check between the two manifests.

Then the bump was simply redone. **This is the "simple corrective bump" timing
the pilot originally set out to measure** — the most common shape in daily life,
and the cheapest:

| Step | Time |
|---|---|
| Fetch, read what the bump contains | ~1 min |
| Re-pin + `yarn install` | **17 s** |
| Confirm the fix is in the shipped bundle (`grep` the class in `dist/index.mjs`) | ~5 s |
| Clear `node_modules/.vite`, restart the dev server | **33 s** |
| Verify against the baseline — both themes × both rail states | **15 s** |
| Rebuild the reference documentation site at the new pin | ~2 min 30 s |
| Step 5b computed-style diff, both themes | ~55 s |
| **Total, hands on keyboard, to a proven visual result** | **≈ 5 min 30 s** |

Full local validation (types, lint, unit tests, production build) runs after
that and is the product's own fixed cost, not the bump's.

**What it changed, measured against the baseline taken before the failed
attempt:** the list's `padding-top` went 8px → **0px**, the first row's top
**76px → 68px**, and — the half that proves nothing else moved — the header's
bottom stayed at **52px** and the footer row stayed at **820px**, in both themes
and both rail states. `padding-bottom` stayed 8px, as intended: only the top
half of the shorthand was the defect. Step 5b then found **zero difference**
against the documentation site on every shared value. **No compensation was
needed, and none was written.** **The refusal to compensate is what made that possible.** Had this
product invented a local workaround — vendoring the build, patching the
manifest through a resolution, freezing a copy of `dist` — three things would
have been true: the library would have received a vaguer report, the guard might
not have been asked for at all, and the product would now own a workaround it
must remember to delete. Instead the pin stayed put for two hours, the report
was the whole deliverable, and the product's tree never carried a line of it.

**And the discipline of measuring "before" paid, exactly as predicted.** The
baseline taken 30 seconds before the failed install — list `padding-top` 8px,
first row top **76px**, header bottom 52px — was still in hand when the bump
finally ran. It turned the verification from "does that look right?" into a
falsifiable check with a number to hit: first row **68px**, header bottom
**52px, unmoved**, footer unmoved, in both themes and both rail states. That is
the difference between confirming a fix and believing one. **Take the baseline
even when — especially when — you suspect the bump will not go through.**

#### How long a pin bump actually takes

Measured end to end on this bump, four compensations removed. These are real
stopwatch numbers from the pilot, not estimates:

| Step | Time |
|---|---|
| Read what the bump contains (`git log <pin>..origin/main`, changesets) | ~2 min |
| Re-pin (`package.json`) + `yarn install` | **13 s** |
| Delete the three compensations, adopt the fourth | **1 min 16 s** |
| Restart the dev server with a cleared dependency cache, verify each removal in the browser | ~2 min |
| Full local validation: type-check, lint, unit tests, i18n gate | **51 s** |
| Production build | **49 s** |
| Rebuild the reference: check out the library at the new pin, `pnpm install`, run its documentation site | ~4 min |
| Systematic computed-style diff — both rail states, both themes, open submenu | ~10 min |
| Update `LIBRARY-FEEDBACK.md` and this playbook | ~6 min |
| **Total, hands on keyboard, from fetch to `git push`** | **≈ 18 min** |
| Continuous integration, 21 jobs including two end-to-end matrices | ~23 min, unattended |

Two things to take from that number. First, **the code change is minutes**: four
deletions and one prop rename, 13 seconds of install. Second, **two thirds of
the time is proof** — re-measuring each removal, rebuilding the reference
documentation site, and re-running the computed-style diff. That ratio is
correct and should not be optimised away; it is what makes a bump a
non-event instead of a regression hunt. It also shrinks: the browser measurement
scripts are reusable, so keep them next to the playbook rather than throwing
them away.

### Procedure

1. **List what you would gain.**
   ```bash
   git -C /tmp/fds fetch origin main
   git -C /tmp/fds log --oneline <CURRENT_PIN>..origin/main
   ```
   Read the changesets and the changelog entries, not just the subjects.
2. **Get the new SHA.**
   ```bash
   git ls-remote https://github.com/XTM-Foundation/filigran-design-system.git main
   ```
3. **Measure the "before" state** of whatever the bump is supposed to move, in
   the running product. Thirty seconds, and it is the only baseline you will
   ever have — it also survives a bump that fails.
4. **Re-pin.**
   ```bash
   yarn up '@filigran/design-system@XTM-Foundation/filigran-design-system#commit=<NEW_SHA>'
   ```
   Confirm both `package.json` and the lockfile moved.

   **If the install fails**, stop and read the packing log — the path is in the
   `YN0058` line, and the real error is inside it, not in yarn's summary. Then
   restore the previous pin, re-install, confirm the product still serves, and
   only then write the report. An install failure is never compensated
   product-side; see [the bump you cannot do](#worked-example--the-bump-you-cannot-do).
5. **Adopt what the bump enables**, in the same pull request — a bump that
   leaves the workarounds in place is a bump you will have to redo.
6. **Remove what the bump makes obsolete.** Every temporary thing you wrote
   carries its own removal condition. Test the condition, then delete the code.
7. **Regenerate the MUI token bridge if the library's `theme.css` moved.**
   The bridge (`fds-tokens.generated.ts` + its `.meta.json`) records a hash of
   the library's `theme.css`; the conformity script reports `bridge-freshness:
   STALE` when the two diverge. Regenerate from the library repository:
   ```bash
   pnpm generate:mui-bridge --product <product> --write-to-product
   ```
   Treat this as **its own single-subject pull request**, not as a rider on a
   component migration: it restyles the whole application, so it needs its own
   visual review. This pilot deliberately left the bridge stale for exactly
   that reason — see the note in the final report.
8. **Re-run the full verification checklist**, including the computed-style
   diff and the visual checkpoint — a bump can move spacing and typography.
9. **Say what changed in the pull-request description**, in plain terms:
   what you gained, what you removed, and what visibly moved.

### Worked example — the bumps queued behind this pilot

| Library change | What the bump gives you | What you must remove or adopt |
|---|---|---|
| Self-defensive styles | Library controls no longer inherit user-agent defaults | ✅ **Done** at pin `d7ea4f2` — both host resets deleted, verified by re-measurement. |
| `Navbar` scroll-list children marked non-shrinking | Rows keep 36px with a long menu | ✅ **Done** at pin `d7ea4f2` — rule deleted, 36px re-measured at the worst case (17 entries, 1000px viewport). |
| Token line-height / letter-spacing corrections | Typography matches the design source | ✅ **Arrived** at pin `d7ea4f2`. Nothing to remove; labels moved from `14.4px / 21.6px / normal` to `14px / 21px / 0.105px`. Re-take the visual checkpoint. |
| `ProductSwitcher` `logoHref` / `logoTo` / `logoCollapsed` | The logo becomes a real home link again in both rails, and the collapsed logo slot stops being 126px wide on a 48px rail | ✅ **Done** at pin `3442003a` — see [the worked removal above](#worked-example--what-did-i-just-delete). |
| `Chip` Enterprise-Edition tone | The EE badge gets its designed colour | ✅ **Adopted** at pin `d7ea4f2`: `severity="neutral"` → `tone="tonic"`. The turquoise comes from the token — a hardcoded primitive would never have been acceptable in the meantime. |
| `NavbarItem` `href` / `to` (requested, entry 1) | Link rows stop needing `asChild` | **Delete** `NavbarRowContent.tsx` and the duplicated library classes it holds; pass `to` directly. This is the single biggest debt reduction available. |
| `Navbar` density adjustments (in flight) | 4px separators, no inter-item gap, submenu group background | Nothing to remove — never compensate a change you know is coming. Re-run [Step 5b](#step-5b--diff-against-the-librarys-own-documentation-site) after the bump and expect the product to *move*. |
| A stacking hook on portalled surfaces (feedback entry 12) | Menus, flyouts and tooltips paint above a host's chrome without a host rule | **Delete** the `[data-radix-popper-content-wrapper]` rule and set `--fds-z-overlay` instead — then re-open a menu over the header in both themes and both rail states. |
| The rail's header/list seam (library PR #77) | The 8px gap between the header and the first row disappears | ✅ **Received** at pin `ad10875`. Nothing to remove — a pure visual correction. First row 76px → **68px**, header bottom and footer unmoved, both themes × both rail states; Step 5b diff clean. |
| Git-dependency installability restored + a consumer-install gate (library PR #79, feedback entry 14) | `main` installs again on every package manager, and the failure class is now blocked at the library's own required job | ✅ **Received** at pin `ad10875` — this is what unblocked #77 above. |
| A real `Menu` component (library PR #74, feedback entry 3) | A composable action/selection menu — controlled `open`, `asChild` trigger, `selected` rows with a check and `aria-current`, items that can be real anchors | ✅ **Done** at pin `9cd4271` — the tenant menu left `NavbarSubmenu` for `Menu`, and measured identical to the documentation site on the first try. |

---

## What belongs to the library vs. to the product

| Concern | Owner | Notes |
|---|---|---|
| Colours, spacing, radii, typography | **Library** | The product never defines a design-system value locally. A missing value is a gap to file, not to improvise. |
| Component chrome: widths, transitions, hover/focus states, tooltips, flyouts | **Library** | If you find yourself restyling library internals from the product, stop and file a gap. |
| Accessibility semantics of a component | **Library** | Except the ones it explicitly delegates (e.g. `aria-current` on your own link). |
| Which items exist in the menu, their order, their icons, their labels | **Product** | |
| Permissions, feature flags, licence gating | **Product** | Preserve behaviour exactly; the library must not learn about them. |
| Routing and link destinations | **Product** | Via `asChild` / `to` today. |
| Persistence of UI state (collapsed, preferences) | **Product** | The library is controlled; it does not remember anything. |
| Product-specific data fetching in a slot | **Product** | |
| The theme class on `<html>` | **Product** | The library reads it; it does not set it. |
| Resetting browser defaults on elements the library renders | **Library** | Was a temporary host prerequisite; closed upstream at pin `d7ea4f2` (self-defensive component styles). Any recurrence is a library bug to file, not a host rule to keep. |
| Sizing the product's own icons for library slots | **Product** | One of the two rules in `design-system-host.css`. |
| Geometry of the product's own content inside a library slot | **Product** | Inline styles, never utility classes — see [5.1](#51-the-stylesheet-is-not-tailwind--do-not-write-utility-classes). |
| Stacking of portalled surfaces above the host's chrome | **Library** | The host knows its own z-scale; the library must let it be set. Compensated here, filed as entry 12. |
| A missing component or prop | **Library** | File it. Never fork, never approximate. |

### The question you will be asked in review: "why isn't this in the library?"

Expect it on **every** file you add. It is a good question and it deserves a
rule rather than a case-by-case answer. Three tests, in order:

1. **Does it know anything about this product?** Routes, permissions, feature
   flags, a storage key, a domain word like *tenant* — if yes, it stays in the
   product. The library cannot depend on a product's vocabulary.
2. **Would every product write the same thing?** If two products would produce
   the same file byte for byte, it belongs to the library. This is the test that
   actually promotes code, and the evidence is empirical: if the next pilot
   re-implements it independently, that is the proof.
3. **Does it only exist because the library is missing something?** Then the
   answer is neither: it is **debt to delete**, not code to promote. Moving a
   workaround into the library freezes the workaround. File the gap instead, and
   let the file disappear when the gap closes.

Worked answers from this pilot, for the four files review asked about:

| File | Verdict | Why |
|---|---|---|
| `MadeByFiligran.tsx` | **Yes — should go to the library** | Filigran branding, identical in every Filigran product, zero product knowledge: an asset, a label, a link. It also carries two non-obvious tricks (the collapsed emblem is the wordmark cropped from the left; a 2px optical offset because a collapsed row reserves 2px for the selection indicator) that should be solved once, not per product. Test 2 is satisfied empirically — the next pilot re-implemented it independently. |
| `nav-menu-model.ts` | No — product | It is the shape of *this* product's menu: its routes, its permissions, its flags. Fails test 1. There is no generic contract to publish either: the library's `Navbar` is composition-based, not data-driven, so it has no menu model to own. |
| `NavbarRowContent.tsx` | No — and it should be **deleted, not promoted** | It exists only because `asChild` makes the library's own `icon`/`showIcon` props inert, so the consumer has to re-declare the row anatomy. That is test 3: promoting it would make the workaround permanent. The gap is filed; the day `NavbarItem` accepts a link destination, this file goes away. |
| `useNavbarState.ts` | No — product | Persistence and cross-component broadcast, bound to the product's own storage key and message bus, and consumed by the product's top bar. The library's `Navbar` is already controlled, which is the correct boundary: the library owns the widget, the product owns where the state lives. Promoting it would force the library to choose a storage key and a messaging mechanism on behalf of every host. |

The short version, worth pasting at the head of your adapter: **the library owns
the widget, the product owns the data, the routes and the state. Anything that
exists only to work around the library is debt with a filed gap, not a
candidate for promotion.**

---

*Written from the OpenAEV navigation pilot. Every trap listed here cost real
time. If you hit one that is not listed, add it — this document is only worth
what the last person put into it.*
