---
applyTo: "**/*"
---

When reviewing code, check for issues in these categories.
Rules are defined in dedicated instruction files — refer to them for the full checklist.

## Security

> Full rules: [security.instructions.md](security.instructions.md)

Key checks: `@AccessControl` on every endpoint, native `@Query` with `WHERE tenant_id`, no `tenant_id` in responses, no hardcoded secrets, no raw error messages to clients.

## Performance

> Full rules: [performance.instructions.md](performance.instructions.md)

Key checks: N+1 queries, `@Fetch(FetchMode.SUBSELECT)` on collections, `FetchType.LAZY` default, `Page<T>` not unbounded `List<T>`, `ReferenceResolver` instead of `findById()` loops, `@Transactional(readOnly = true)` on reads.

## Architecture

> Full rules: [backend.instructions.md](backend.instructions.md)

Key checks: layering (Controller → Service → Repository, never skip), JPA entities never returned from controllers (use DTOs), `@Transactional` self-call (Spring proxy bypass), no new code in `openaev-framework` (deprecated).

## Test Quality

> Full rules: [testing.instructions.md](testing.instructions.md)

Key checks: `@Nested` + `@DisplayName` grouping, `given_X_should_Y` naming, AAA comments, OpenAEV's `@WithMockUser` (not Spring's), Fixture + Composer (no inline data).

## Frontend

> Full rules: [frontend.instructions.md](frontend.instructions.md)

Key checks: no MUI for layout (native HTML), `sx` prop only (no `makeStyles`), `t()` called early, auto-generated `api-types.d.ts` (no manual types).

## Review Style

- Use **conventional comments**: `suggestion:`, `issue:`, `todo:`, `nitpick:`, `praise:`
- Add `(blocking)` or `(non-blocking)` decoration
- Be specific, actionable, explain the "why"
- When flagging a rule violation, mention which instruction file defines the rule
