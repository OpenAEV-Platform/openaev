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

Key checks: layering (Controller → Service → Repository, never skip), JPA entities never returned from controllers (use DTOs), `@Transactional` self-call (Spring proxy bypass), no new code in `openaev-framework` (deprecated), no new code in `io.openaev.rest` (legacy — use `io.openaev.api` instead), backend EE-only behavior explicitly marked with `@AccessControl(..., isEnterpriseEdition = true)` so Enterprise Edition validation is enforced.

## Multi-Tenancy

> Full rules: [multi-tenancy.instructions.md](multi-tenancy.instructions.md)
> Review skill: [review-multi-tenancy](../skills/review-multi-tenancy/SKILL.md)

Key checks: tenant-scoped entities extend `TenantBase` + `@Filter("tenantFilter")`, tenant resolved in API layer and passed as argument to services (services never call `TenantContext` directly), native `@Query` has `WHERE tenant_id`, no `tenant_id` in API responses (`@JsonIgnore`), unique constraints are composite `(field, tenant_id)`, background jobs set `TenantContext` + pass tenant as arg, caches include tenant key. **Dual-scope entities** (Settings, User, Role, Group): implement `DualScopeBase`, nullable `tenant_id`, repository extends `DualScopeRepository`, two services (`PlatformXxxService` / `TenantXxxService`), two APIs, no unscoped `findAll()`.

## Test Quality

> Full rules: [testing.instructions.md](testing.instructions.md)

Key checks: `@Nested` + `@DisplayName` grouping, `given_X_should_Y` naming, AAA comments, OpenAEV's `@WithMockUser` (not Spring's), Fixture + Composer (no inline data), constants shared via static import (never duplicated between source and test), public endpoints tested without `@WithMockUser`.

## Frontend

> Full rules: [frontend.instructions.md](frontend.instructions.md)
> Agent: `frontend-reviewer`

Key checks: no MUI for layout (native HTML), `sx` prop only (no `makeStyles`), `t()` called early, auto-generated `api-types.d.ts` (no manual types), feature-flagged behavior uses the correct frontend flag check, and EE-only UI/actions are gated by frontend Enterprise Edition validation (typically `useEnterpriseEdition().isValidated`).

## Chaining Engine

> Full rules: [chaining-engine.instructions.md](chaining-engine.instructions.md)
> Review skill: [review-chaining-engine](../skills/review-chaining-engine/SKILL.md)
> Agent: `chaining-engine-reviewer`

Key checks: step lifecycle (TEMPLATE → READY → RUN → END), `workflowService.isWorkflowEnded()` guard before execution, queue interactions only via `QueueChainingService`, global state updated before local propagation, time delays via `StepDelayQueueService` (never `Thread.sleep()`), `@WorkflowUpdateEvent` on inject-mutating methods.
EE check: chaining EE-only endpoints/operations are explicitly marked with `@AccessControl(..., isEnterpriseEdition = true)` so AccessControlAspect enforces Enterprise Edition license validation.

## Review Style

- Use **conventional comments**: `suggestion:`, `issue:`, `todo:`, `nitpick:`, `praise:`
- Add `(blocking)` or `(non-blocking)` decoration
- Be specific, actionable, explain the "why"
- When flagging a rule violation, mention which instruction file defines the rule
