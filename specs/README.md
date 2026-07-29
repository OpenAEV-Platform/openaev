# specs/ — spec-driven development

OpenAEV uses a lightweight, Spec-Kit-style workflow so that non-trivial work is
traceable to an intent, not just a diff (see Article 1 of
[`.github/instructions/constitution.instructions.md`](../.github/instructions/constitution.instructions.md)).

## The workflow

| Step | Command | Produces | Question it answers |
|---|---|---|---|
| 0 | `/mockup` (optional) | `mockup/*.html` + `mockup/handoff.md` | **What it looks like** — hi-fi mockup, iterated with the user |
| 1 | `/specify` | `spec.md` | **What** & **why** (no solution design) |
| 2 | `/plan` | `plan.md` | **How** — modules, data, API, tests, reviewers |
| 3 | `/tasks` | `tasks.md` | The ordered, commit-sized checklist |
| 4 | `/implement` | code + commits | Execution, gated by the reviewer agents |

The `/constitution` command maintains the governing principles all four steps cite.

For full features, `/feature` orchestrates all phases and enforces the **gate
boards** defined in
[`.github/instructions/feature-workflow.instructions.md`](../.github/instructions/feature-workflow.instructions.md):
five gates (product, design, staff, security, docs) validate the spec package
before implementation (blocker loop until 5× GO in `gates.md`), and the same
five gates review the delivery afterwards (with a CVSS protocol on security
findings).

These commands work in **both** assistants:

- **GitHub Copilot** — the prompt files under `.github/prompts/*.prompt.md`.
- **Claude Code** — the same files, surfaced as slash commands via generated
  adapters under `.claude/` (bridge to be shared in a follow-up). `.github/` is
  the single source of truth.

## Layout

```
specs/
  NNN-feature-slug/
    mockup/     # /mockup (optional): *.html + handoff.md
    spec.md     # /specify
    plan.md     # /plan
    tasks.md    # /tasks
    gates.md    # gate boards verdicts + blockers log (/feature)
```

`NNN` is a zero-padded incrementing id (`001`, `002`, …). One directory per
feature. Keep specs in the repo — they are the durable record of intent behind a
change and outlive the PR description.
