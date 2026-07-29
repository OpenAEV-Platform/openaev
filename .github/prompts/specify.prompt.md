---
description: "Spec-driven step 1/4 — turn an intent into a feature spec (WHAT & WHY, no HOW) under specs/."
---

You are running **`/specify`**, step 1 of the OpenAEV spec-driven workflow
(`/specify` → `/plan` → `/tasks` → `/implement`). See `specs/README.md`.

Governing rules: `.github/instructions/constitution.instructions.md` (esp. Article 1
— spec before code). Routing: `AGENTS.md`.

## Goal

Capture **what** to build and **why**, from the user's description. Do **not**
design the solution — no file names, no schemas, no libraries. That is `/plan`.

## Procedure

1. Read the constitution and, if the feature area is obvious, skim the matching
   `.github/instructions/*.md` for domain vocabulary (do not design yet).
2. **Mockup handoff** (Feature Workflow phase 0): if a validated
   `specs/NNN-slug/mockup/handoff.md` exists for this feature, it is your primary
   input — reuse that directory/id, and make the spec cover every decision and
   screen recorded there.
3. Otherwise pick the next spec id: the highest existing `specs/NNN-*` directory
   + 1, else `001`. Slugify the feature into `NNN-short-kebab-slug`.
4. Write `specs/NNN-slug/spec.md` using the template below.
5. Mark every genuinely undecided point with `[NEEDS CLARIFICATION: …]` rather than
   guessing. List them all under **Open questions**.
6. **Spec board gates** (see `.github/instructions/feature-workflow.instructions.md`):
   run the `product-gate` and `design-gate` agents in **spec mode** on the result
   and record their verdicts in `specs/NNN-slug/gates.md`. On BLOCKER, apply the
   blocker protocol (fix the target artifact, re-run) until both record GO.
   The three remaining gates (staff, security, docs) run at `/tasks`.
7. Summarize the spec, the gate verdicts and the open questions; tell the user to
   run `/plan` when the spec reads right.

## Template — `spec.md`

```markdown
# Spec: <Feature name>

- **ID**: NNN-slug
- **Issue**: #<n> (if any)
- **Mockup**: ./mockup/handoff.md (if the feature went through /mockup)
- **Status**: draft

## Intent (why)
<1–3 sentences: the problem and the desired outcome. Business/user value.>

## Scenarios
<Concrete user-facing scenarios in Given/When/Then form. The acceptance surface.>

## Functional requirements
- FR1: The system MUST …
- FR2: …
<Each testable and unambiguous. Use MUST/SHOULD/MAY.>

## Out of scope
<What this feature explicitly does NOT do — prevents scope creep (Article 9).>

## Constraints & impacts
- Multi-tenancy: <tenant-scoped? new tenant data? — flags Article 7 / reviewers>
- Security / permissions: <new capability, access-control surface?>
- Data / migration: <new persisted data? — flags a Flyway migration in /plan>

## Open questions
- [NEEDS CLARIFICATION: …]
```

Argument (optional): the feature description. If absent, ask the user what to spec.
