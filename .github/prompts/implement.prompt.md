---
description: "Spec-driven step 4/4 — execute the task list, run gating reviewers, and commit per project conventions."
---

You are running **`/implement`**, step 4 of the OpenAEV spec-driven workflow
(`/specify` → `/plan` → `/tasks` → `/implement`). See `specs/README.md`.

Governing rules: `.github/instructions/constitution.instructions.md` (esp.
Article 4 — fix the root cause; Article 10 — reviewer agents gate merge;
Article 11 — commit hygiene). Routing: `AGENTS.md`.

## Goal

Turn `tasks.md` into working, reviewed, committed code — one task at a time.

## Procedure

1. Read `specs/NNN-slug/tasks.md`, `plan.md`, and `spec.md`. Load every
   `.github/instructions/*.md` the plan marked applicable, and the relevant
   `.github/skills/*/SKILL.md` (e.g. `add-migration`, `add-test`,
   `create-feature-module`) — follow them step by step.
2. For each unchecked task, in order:
   a. Implement it, complying with the applicable instructions.
   b. Build / type-check / test the affected surface (see `AGENTS.md` Key Commands;
      format Java with `/format`).
   c. Run the **gating reviewer agent(s)** named on the task (Article 10). Resolve
      every blocking finding — fix the root cause in the correct layer (Article 4),
      not a workaround.
   d. Commit as one logical Conventional Commit ending with `(#issue)`, signed,
      no bracket prefixes (Article 11). Check the task off in `tasks.md`.
3. When all tasks are done, run the `code-reviewer` hub over the full diff plus any
   specialized reviewer the plan flagged; confirm the scenarios in `spec.md` hold.
4. **Delivery review board** (see
   `.github/instructions/feature-workflow.instructions.md`): run the five gate
   agents in **review mode** — `staff-gate` (everything planned was delivered),
   `security-gate` (secure delivery; applies the CVSS protocol: < 7.0 → GitHub
   issue, ≥ 7.0 → FAIL, board blocked), `product-gate` (acceptance criteria
   verified), `docs-gate` (documentation updated), `design-gate` (UI matches the
   validated mockup and the design system). Record verdicts in
   `specs/NNN-slug/gates.md`; on FAIL, fix and re-run the failing gate plus any
   gate whose input changed. The feature is done only when the board passes.
5. Report: what shipped, both boards' verdicts, security issues opened
   (CVSS < 7.0), and anything deferred as a follow-up (Article 9 — do not fold
   unrelated fixes into this change).

## Guardrails

- Do not start if the feature went through the Feature Workflow and
  `specs/NNN-slug/gates.md` does not show **5× GO** on the spec board.
- Never mark a task done with failing tests, unresolved reviewer blockers, or a
  partial implementation.
- Stay within the plan's scope; surface newly discovered work as follow-up tasks,
  don't silently expand the PR.
- Keep the change behind its feature flag if the spec/plan requires one.

Argument (optional): the spec id / slug, or a specific task id to implement. If
absent, use the most recent `specs/*` with an unfinished `tasks.md`, or ask.
