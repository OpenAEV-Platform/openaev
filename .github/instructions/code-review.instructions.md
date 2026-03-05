---
applyTo: "**/*"
---

When reviewing code, check for these categories of issues:

## Security
- Hardcoded secrets, credentials, API keys
- Missing `@AccessControl` on endpoints
- Native `@Query` without `WHERE tenant_id` (bypasses tenant filter)
- `tenant_id` exposed in API responses
- Raw error messages / stack traces sent to clients

## Performance
- N+1 queries (missing `@Fetch(FetchMode.SUBSELECT)`)
- `deleteById()` on heavy entities without native `@Query` alternative
- Immutable collections on JPA entities (`List.of()` instead of `new ArrayList<>()`)

## Architecture
- Repository injected in a controller instead of going through a Service
- Business logic in controller instead of service
- JPA entity returned directly from new controller (should use DTO)
- `@Transactional` on self-call (Spring proxy bypassed)
- New code added to `openaev-framework` (deprecated)

## Test Quality
- Missing `@Nested` + `@DisplayName` grouping
- Method not following `given_X_should_Y` naming
- Missing AAA comments (`// Arrange` / `// Act` / `// Assert`)
- Using Spring's `@WithMockUser` instead of OpenAEV's custom one
- Inline test data instead of Fixture class + Composer

## Frontend
- MUI used for layout (`Box`, `Grid`) instead of native HTML
- `makeStyles` / `withStyles` instead of `sx` prop
- `t()` called deep in component tree instead of early
- Manual types instead of auto-generated `api-types.d.ts`

## Review Style
- Use **conventional comments**: `suggestion:`, `issue:`, `todo:`, `nitpick:`, `praise:`
- Add `(blocking)` or `(non-blocking)` decoration
- Be specific, actionable, explain the "why"
