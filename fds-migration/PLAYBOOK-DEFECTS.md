# Playbook defect register

Every defect found by running this playbook against a real product. The register
exists so that the next pilot inherits the corrections instead of rediscovering
the gaps.

**Source.** 43 defects recorded while pilot 2 (OpenCTI, `fds-navbar`) followed
the playbook end to end, of which 18 were re-verified by an independent review
that **executed** the playbook's commands rather than reading them.

**Severity.** *Blocker* — the follower cannot proceed, or proceeds on a false
premise. *Major* — costs a round trip or produces wrong output. *Minor* — slows
the follower down.

**Blockers: six.** D04, D05, D12, D27, D29, D39. An earlier count of five was
wrong; it is restored here, because the number is what tells the next reader how
much of this playbook was load-bearing and missing.

---

## Corrections applied to this register

Four entries carried a diagnosis or a proposed fix that was itself wrong. A
defect register that is wrong about its own causes makes the playbook worse, not
better — it teaches the error with the authority of a finding. Each correction
below was established **by executing the command or reading the file**, not by
re-reading the register.

### D08 — the proposed fix filters out what it claims to search

The register's replacement command was:

```bash
grep -rn "yarn install\|npm ci\|pnpm install" .github/ --include=Dockerfile\* -r .
```

Executed on OpenCTI's tree, this returns **6 hits, all in Dockerfiles, and none
of the 23 workflow hits**. `--include` applies to the *whole* invocation, so
adding it to catch Dockerfiles silently discards every workflow match — the
exact set the original command existed to find. The corrected fix is **two
separate searches**, and the playbook now says so explicitly, with the reason.
The underlying defect stands: the original command was scoped to `.github/` and
could not see container builds.

### D04 — "the playbook never mentions containers" is not accurate

The playbook did discuss container steps: it names `shell: alpine.sh`, explains
why a composite action cannot run in those blocks, and tells the follower to
inline the auth lines there. What it lacked was the **container image** model —
BuildKit build secrets for `Dockerfile` installs, and `-e` propagation for
`docker run`. Restating the defect precisely matters: a follower who reads
"never mentions containers" and then finds container content concludes the
register is unreliable and stops trusting the rest of it. Severity unchanged:
**blocker**.

### D39 — "points at OpenAEV's playbook file only" is not accurate

The playbook carries **3** references to paths under `openaev-front/`, i.e. to
the previous pilot's implementation, not only to its playbook. (The review
counted 4, reading a slightly earlier revision; the substance is unaffected.)
The real defect is that those references are incidental examples, and the
playbook never *instructs* the follower to read the previous pilot's source as a
step with a success criterion. That is what Step 0.5 now does. Severity
unchanged: **blocker** — it cost pilot 2 a full checkpoint round trip.

### D26 / D43 — one defect, recorded twice with inverted severity

Both say: read the target repository's pull-request conventions before opening
the PR. D26 is marked *major*, D43 *minor*. They are merged here as a single
**major** entry (D26), with D43's specific and more useful finding folded in —
title checks **arm per target branch**, so a convention that is not enforced on
the default branch may be enforced on `design-system/current`, and the follower
discovers it at push time.

---

## Register

| ID | Sev | Playbook section | Defect | Status |
|---|---|---|---|---|
| D01 | major | §1.3 | `git log --oneline --left-right --count` prints a commit list, not the two numbers the prose promises — `--oneline` suppresses `--count`. | Fixed: use `git rev-list --left-right --count`. |
| D02 | major | §1.3, Step 3 | The product default branch is hardcoded to `main`; OpenCTI's is `master`, so every command fails verbatim. | Fixed: parameterised, with `git remote show origin \| grep "HEAD branch"` as the first move. |
| D03 | minor | §1.2 | The secret is a *repository* secret; a job pinned to an `environment:` does not see it. | Fixed: verify scope, check no install job runs under an environment. |
| **D04** | **blocker** | Step 2 | Containerised installs are not covered: runner-side `git config` does not cross the image boundary, so the documented recipe authenticates nothing where installs run in containers. | Fixed: "Arm the containerised install sites" — BuildKit secret and `-e` propagation. |
| **D05** | **blocker** | Step 2 | The image must contain `git`; Alpine node images do not, and the symptom reads as an auth failure. | Fixed: verification command and the shared-stage rule. |
| D06 | major | Step 2 | `set -x` in an existing container script traces the expanded git config line, printing the token. | Fixed: `set +x` bracket, and `set -eu` not `-eux` in the Dockerfile recipe. |
| D07 | major | Step 2 | Double-quoted `sh -c "…"` interpolates on the **host**, putting the token on the docker command line. | Fixed: single-quote rule, pass by name with `-e`. |
| D08 | major | Step 2 | The enumeration grep is scoped to `.github/` and structurally cannot find installs in Dockerfiles. | Fixed — **with a corrected fix**, see above. |
| D09 | major | Step 4 | Which `.yarnrc.yml` to edit is a guess: a repo may have several, and the Dockerfile copies only one into the image. | Fixed: resolve the file Yarn actually uses **and** the one the Dockerfile copies; they must match. |
| D10 | major | Step 10 | `yarn i18n-checker` is prescribed as a mandatory gate with OpenAEV paths; in OpenCTI the script does not exist and no CI job enforces i18n. | Fixed: made product-agnostic, with the silent-degradation failure mode stated. |
| D11 | major | Step 6 | The theme snippet assumes one writer and a literal `light`/`dark`; products have several writers and resolve by theme *name*. | Fixed: grep for every writer; custom theme names fall into the dark branch. |
| **D12** | **blocker** | Step 6 | No coverage of colours the legacy component reads from a **user-customisable** theme: adopting library tokens is a functional loss, not a visual delta. | Fixed: new Step 6b, custom-theme audit, with escalation before implementing any accepted loss. |
| D13 | minor | Step 6 | A pre-existing *scoped* theme-class helper should be amended, not shadowed by a second writer. | Fixed in the Step 6 traps. |
| D14 | major | Step 9 | `git rm -r <folder>` assumes the legacy code is an isolable folder; it may hold files that must survive, and be a constants module for the rest of the app. | Fixed: check inbound references per file, not per folder. |
| D15 | major | Step 10 | The minimum test list omits the product's *other* consumers of the nav contract — storage key, broadcast channel, width constants. | Fixed. |
| D16 | minor | Final checklist | The conformity script may already exist on the target branch, and it writes a report file as a side effect. | Fixed: check before creating; do not commit the report. |
| D17 | major | Step 2 | The "image must contain git" rule applies to `docker run` sites too, not only to Dockerfiles. | Fixed. |
| D18 | major | Step 2 | A composite action **cannot read the `secrets` context**; the value must be a declared input wired at every caller, and resolves to empty otherwise. | Fixed: propagation rules table, and asserted by the guard test. |
| D19 | minor | Step 2 | Builds targeting an earlier stage that never installs need no secret; arming them is leak surface for nothing. | Fixed, and asserted by the guard test in both directions. |
| D20 | minor | Step 4 | The expected `ls` output is a strict subset of the real build output. | Fixed: check for the entry point, not for an exact listing. |
| D21 | major | Step 8 + 10 | Real anchors force `asChild`, which makes the library's `icon` props inert — a contradiction between two success criteria. | Fixed: documented as the re-composition case, with the anatomy-copying rule. |
| D22 | major | Step 10 | Re-serialising locale files produces a ~2 500-line diff per file for three added keys. | Fixed: edit in place, match the checked-in formatting. |
| D23 | major | Step 8 + 10 | One component that both loads data and renders cannot be unit-tested against the rendering contract. | Fixed: split the data boundary from the presentational component. |
| D24 | minor | Step 7 | Library components may carry hardcoded English strings; grep the built bundle. | Fixed. |
| D25 | minor | Step 4 | The type-checker fails on missing generated modules until the codegen has run, which reads as a broken adapter. | Fixed: codegen before type-check. |
| D26 | major | Before Step 1 + final checklist | The target repository's PR conventions are not read: a machine-checked title format with a mandatory issue number, **and such checks arm per target branch**. | Fixed (merged with D43). |
| **D27** | **blocker** | Step 2 | The install-site enumeration is a flat grep; reusable workflows do not inherit caller secrets, and composite actions need per-caller wiring. | Fixed: Step 2 rewritten around the call graph, **plus a guard test that performs the enumeration**. |
| D28 | major | Step 7 | The playbook stops at a green CI; three defects were invisible to every automated check. | Fixed: running the product is a blocking step, with scripted assertions. |
| **D29** | **blocker** | Step 5 | Overriding a library custom property does not reach tokens **derived** from it: `color-mix()` is substituted where the declaration lives. | Fixed: the derived-token trap in Step 6b, with the extraction command. |
| D30 | major | Step 5 | The non-regression guard pattern is satisfiable by a substring match that passes while the product is visibly wrong. | Fixed: compare **sets** of tokens, not substrings. |
| D31 | major | Step 4 | The old rail may carry `flex: 0 0 auto` that the library `<nav>` does not; the rail silently shrinks and every width-derived measurement is wrong. | Fixed: assert the rendered width, do not assume the swap is neutral. |
| D32 | major | Step 3 | The e2e inventory misses specs that reach the old component by implementation detail (a MUI icon test id). | Fixed. |
| D33 | major | Step 3 | The library submenu keeps closed panels **mounted**; page-wide `getByLabel` queries become ambiguous. | Fixed. |
| D34 | minor | Step 12 | The e2e suite runs with `-x`, so each round trip reveals exactly one problem; discovery is serial. | Fixed: budget for it. |
| D35 | major | Step 6 | Playwright non-waiting probes (`count()`) choose the wrong branch while a modal is still closing. | Fixed. |
| D36 | major | Step 6 | A component can emit **different roles for the same item** depending on its own state. | Fixed. |
| D37 | minor | Step 8 | No triage rule for a red job unrelated to the change. | Fixed: date it, re-run on the same SHA, then classify. |
| D38 | major | Step 3 | Accessible-name contracts on the **non-row** parts of the rail are not inventoried. | Fixed. |
| **D39** | **blocker** | whole playbook | The follower is never told to read the **previous pilot's implementation**; three of four checkpoint findings had already been solved. | Fixed: Step 0.5 + pilot index + standing rule. |
| D40 | major | Step 8 | The parity checklist omits **layout properties of the rail itself** — full height, fixed vs scrolling, position in the shell. | Fixed. |
| D41 | major | Step 6 | A prop can mean two different things depending on the component's mode. | Fixed. |
| D42 | minor | Step 5 | Utility classes the product writes are inert without a Tailwind build; only what the library emits exists. | Fixed: the three-way class rule. |
| ~~D43~~ | — | — | Duplicate of D26, with inverted severity. | Merged into D26. |
