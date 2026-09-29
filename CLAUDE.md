@AGENTS.md

# Claude Code specifics

`AGENTS.md` (imported above) is the single source of truth, shared with GitHub Copilot.
Do not duplicate conventions here: this file only explains how Claude Code should consume
the Copilot-oriented files it references.

## Path-scoped instructions (`.github/instructions/*.instructions.md`)

Claude Code does **not** honour the Copilot `applyTo` frontmatter, so these files are never
loaded automatically. Before creating or modifying a file, **Read every instruction file whose
`applyTo` glob matches it** (the table in `AGENTS.md` → "Where to find conventions" gives the
mapping). Also read [.github/copilot-instructions.md](.github/copilot-instructions.md) once per
session before any code change.

Quick map:

| Touching... | Read first |
|---|---|
| any Java / TS file | `security.instructions.md`, `code-review.instructions.md` |
| `openaev-api/**`, `openaev-model/**` Java | `backend`, `performance`, `orm`, `multi-tenancy` |
| `io/openaev/api/**`, `io/openaev/rest/**` | `api` |
| entities / repositories / `**/migration/**` | `database`, `multi-tenancy` (+ `migration` for `io/openaev/migration/**`) |
| `openaev-front/**` | `frontend` |
| tests (`*Test.java`, `*.test.*`, `tests_e2e/**`) | `testing` (+ `orm` for `openaev-api` Java tests) |
| `**/chaining/**`, `QueueChainingJob`, `WorkflowTimeoutJob` | `chaining-engine` |

## Skills

`.claude/skills/*` are symlinks to `.github/skills/*`, so every skill listed in `AGENTS.md` is
available as a Claude Code skill (e.g. `/add-migration`, `/review-security`). Edit skills in
`.github/skills/` only — never in `.claude/skills/`. When adding a new skill under
`.github/skills/<name>/`, add the matching symlink:
`ln -s ../../.github/skills/<name> .claude/skills/<name>`.

## Specialized agents (`.github/agents/*.agent.md`)

These are Copilot agent definitions (their `tools:` frontmatter is Copilot-specific). To use one
from Claude Code, spawn a subagent with the `Agent` tool and instruct it to first read
`AGENTS.md`, then the given `.github/agents/<name>.agent.md`, and follow it (including the
context-loading order and skill it references). For a full review, run the Code Reviewer first,
then the specialists it lists — in parallel.

## Workflow reminders

- Cross-layer tasks: present the scope summary and wait for confirmation before writing code.
- After Java changes run `mvn spotless:apply`; after frontend changes run `yarn lint && yarn check-ts`
  in `openaev-front/`.
- PRs: follow `.github/PULL_REQUEST_TEMPLATE.md` exactly; commits follow Conventional Commits
  with the issue reference and must be signed.
