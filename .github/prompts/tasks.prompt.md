---
description: "Spec-driven step 3/4 — break a plan into small, ordered, independently reviewable tasks."
---

You are running **`/tasks`**, step 3 of the OpenAEV spec-driven workflow
(`/specify` → `/plan` → `/tasks` → `/implement`). See `specs/README.md`.

Governing rules: `.github/instructions/constitution.instructions.md` (esp.
Article 9 — keep the change focused; Article 11 — small logical commits).
Reviewer routing: `AGENTS.md`.

## Goal

Decompose an approved `plan.md` into an ordered checklist of small tasks, each
mapping to roughly one logical commit and naming the reviewer agent that gates it.

## Procedure

1. Read `specs/NNN-slug/plan.md` (and `spec.md` for acceptance context).
2. Write `specs/NNN-slug/tasks.md` using the template below.
3. Order tasks bottom-up so each leaves the tree building/green: model →
   migration → API → frontend → tests/docs. Mark tasks that can run in parallel
   with `[P]`.
4. For each task name the gating reviewer (from `AGENTS.md`): e.g. migration →
   `migration-reviewer`; anything touching tenancy → `multi-tenancy-reviewer`;
   frontend → `frontend-reviewer`; always the `code-reviewer` hub.
5. Turn every entry of the plan's **Documentation impact** section into an
   explicit task (page path, screenshot refresh if the UI changes).
6. **Spec board gates** (see `.github/instructions/feature-workflow.instructions.md`):
   run the `staff-gate`, `security-gate` and `docs-gate` agents in **spec mode**
   and record their verdicts in `specs/NNN-slug/gates.md`. On BLOCKER, apply the
   blocker protocol — the target may be `tasks.md` but also `spec.md` or
   `plan.md`; fix it, re-run the blocking gate and every gate whose input
   changed (including product/design from `/specify` if the spec moved).
7. Summarize the tasks and the board state; implementation may start only when
   `gates.md` shows **5× GO**. Tell the user to run `/implement`.

## Template — `tasks.md`

```markdown
# Tasks: <Feature name>

- **Plan**: ./plan.md
- **Status**: draft

> One task ≈ one logical commit (Article 11). `[P]` = parallelizable.

- [ ] T1 — <imperative task title>
  - Files: <paths>
  - Done when: <observable outcome / which FR it satisfies>
  - Reviewer: <agent from AGENTS.md>
- [ ] T2 [P] — …
  - Files: …
  - Done when: …
  - Reviewer: …
```

Argument (optional): the spec id / slug. If absent, use the most recent `specs/*`
with a `plan.md` but no `tasks.md`, or ask.
