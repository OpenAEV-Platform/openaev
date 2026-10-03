---
description: "Spec-driven step 2/4 — turn a spec into a technical plan (HOW) citing the constitution and domain instructions."
---

You are running **`/plan`**, step 2 of the OpenAEV spec-driven workflow
(`/specify` → `/plan` → `/tasks` → `/implement`). See `specs/README.md`.

Governing rules: `.github/instructions/constitution.instructions.md`. Routing and
the list of domain instruction files + reviewer agents: `AGENTS.md`.

## Goal

Turn an approved `spec.md` into a concrete **technical plan** that respects module
boundaries and the constitution. This is where **HOW** lives.

## Procedure

1. Read the target `specs/NNN-slug/spec.md`. If it still has unresolved
   `[NEEDS CLARIFICATION]`, resolve them with the user before planning.
2. Read the constitution. From `AGENTS.md`, identify **every** domain
   `.github/instructions/*.md` whose `applyTo` will match the files you expect to
   touch, and read them. The plan MUST comply with each.
3. Write `specs/NNN-slug/plan.md` using the template below.
4. Call out any constitution deviation explicitly with a written justification
   (Article intro rule). If the change touches `@AccessControl`/`@Filter`/
   `Capability`/native `@Query`/`TenantBase`/`tenant_id`, note that Security /
   Multi-Tenancy reviewers gate it (Article 7 & 10).
5. Summarize the plan and the reviewers it will require; tell the user to run
   `/tasks`.

## Template — `plan.md`

```markdown
# Plan: <Feature name>

- **Spec**: ./spec.md
- **Status**: draft

## Approach
<A few paragraphs: the technical strategy end-to-end.>

## Affected modules
<openaev-model / openaev-api / openaev-front — and why. Respect Article 2:
new backend code in io/openaev/api/**, nothing new in openaev-framework.>

## Design
- **Data model / migration**: <entities, columns; Flyway migration needed?
  → .github/skills/add-migration>
- **API surface**: <endpoints, Input/Output records + Mapper (Article 3 — never
  expose entities)>
- **Frontend**: <pages/components/hooks, permissions, i18n>
- **Chaining engine**: <if steps/conditions/queues are involved>

## Applicable instructions
<List each .github/instructions/*.md that governs this change and the key rules
it imposes here.>

## Security & multi-tenancy
<Access control, capabilities, filters, tenant scoping. Which reviewers gate it.>

## Test strategy
<What to test and at what level, proportionate to risk (Article 6). Tenant-isolation
tests if tenant-scoped.>

## Documentation impact
<Which docs/ pages change or are created; screenshots affected? "No doc impact"
needs a one-line justification. Gated by docs-gate at /tasks.>

## Risks & deviations
<Known risks; any justified deviation from the constitution.>
```

Argument (optional): the spec id / slug to plan. If absent, use the most recent
`specs/*` without a `plan.md`, or ask.
