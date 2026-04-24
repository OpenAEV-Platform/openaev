---
name: spec-plan
description: >-
  Generates an implementation plan from a validated spec. Decomposes the feature into
  phased tasks mapped to OpenAEV modules. Use after spec creation is complete.
---

# Create Implementation Plan

## Prerequisites

- A validated spec at `.github/specs/SPEC-{NNN}-{name}/spec.md` with status "Ready"
- Access to `.github/templates/plan-template.md` and `.github/templates/tasks-template.md`

## Procedure

### Step 1 — Load Context

1. Read the spec file: `.github/specs/SPEC-{NNN}/spec.md`
2. Read the constitution: `.github/specs/constitution.md`
3. Read the plan template: `.github/templates/plan-template.md`
4. Read the tasks template: `.github/templates/tasks-template.md`
5. Scan existing code for similar features (for reference patterns):
   ```bash
   # Find similar entities/services
   find openaev-model/src/main/java -name "*.java" | head -20
   find openaev-api/src/main/java/io/openaev/api -type d | head -20
   ```

### Step 2 — Constitution Check

Validate the spec against all 8 constitution principles:

| Principle | Check |
|-----------|-------|
| I. Security-First | @AccessControl planned? Tenant isolation addressed? |
| II. Layered Architecture | Module mapping follows Controller→Service→Repository? |
| III. Test-First | Tests defined in spec §6? Coverage targets set? |
| IV. Performance | Pagination for lists? LAZY loading? ReferenceResolver? |
| V. Spec-Driven | Spec exists and is validated? |
| VI. Conventional Commits | Branch from release/current? |
| VII. Frontend Discipline | api-types.d.ts? Zod? CASL? |
| VIII. Simplicity | No over-engineering? Reuse existing components? |

If any principle fails, update the spec or flag the issue.

### Step 3 — Generate the Plan

Create `.github/specs/SPEC-{NNN}/plan.md` using the template:

1. **Summary** — from spec §1
2. **Technical Context** — OpenAEV stack (fixed)
3. **Constitution Check** — results from Step 2
4. **Architecture** — module mapping with exact file paths:
   - Entity → `openaev-model/src/main/java/io/openaev/database/model/`
   - Repository → `openaev-model/src/main/java/io/openaev/database/repository/`
   - Service → `openaev-api/src/main/java/io/openaev/service/`
   - DTOs + Mapper + Controller → `openaev-api/src/main/java/io/openaev/api/{feature}/`
   - Migration → `openaev-api/src/main/java/io/openaev/migration/`
   - Tests → `openaev-api/src/test/java/io/openaev/`
   - Frontend → `openaev-front/src/`
5. **Database Schema** — from spec §5
6. **API Contract** — from spec §5
7. **Implementation Phases** — 5 phases (DB → Backend → Tests → Frontend → Validation)
8. **Risks & Mitigations** — from spec context
9. **Dependencies** — ordering constraints

### Step 4 — Generate the Task Breakdown

Create `.github/specs/SPEC-{NNN}/tasks.md` using the template:

1. Map each user story to specific tasks
2. Identify parallel opportunities (mark with [P])
3. Set dependencies between phases
4. Include exact file paths for every task
5. Add security and performance tasks from spec requirements

### Step 5 — Find Next Migration Number

```bash
ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -5
```

Record the next migration number in the plan.

### Step 6 — Verify & Commit

1. Review plan + tasks for completeness
2. Ensure every acceptance criterion from spec maps to at least one task
3. Ensure every security requirement maps to a test task

```bash
git add .github/specs/SPEC-{NNN}/plan.md .github/specs/SPEC-{NNN}/tasks.md
git commit -m "[agent] feat(spec): create plan for SPEC-{NNN} — {feature name}"
```

The plan is now ready for `/spec implement`.
