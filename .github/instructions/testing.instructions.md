---
applyTo: "**/*Test.java,**/*Test*.java,**/test/**,**/*.test.*,**/*.spec.*,**/tests_e2e/**"
description: "Testing conventions: integration tests, unit tests, fixtures, composers, assertions"
---

# Testing Conventions

## How Many Tests

- **The fewest tests that cover all the new code**: one integration test per flow, parameterized tests
  for the list of refusal or error cases
- No test that restates configuration (one test per enum value, per mapping entry)
- Test through the public API: no direct test of a private method, of framework behaviour (Spring Security
  config, Hibernate internals) or of exact error wording (assert the status and error structure)
- Delete the tests of removed code in the same PR

## Integration Tests (API)

- Extend `IntegrationTest`
- `@TestInstance(PER_CLASS) @Transactional` on the class
- `@WithMockUser` → `io.openaev.utils.mockUser.WithMockUser` (NOT `org.springframework`)
- Group with `@Nested` + `@DisplayName`
- **Method naming**: `given_X_should_Y` → e.g. `given_validInput_should_createGroup()`, `given_crowdstrike_should_not_LaunchAtomicTesting()`
- **AAA pattern**: `// Arrange` / `// Act` / `// Assert`
- JSON: `assertThatJson(response).node("field").isEqualTo(...)` (json-unit library)
- URI constant at class level: `public static final String FEATURE_URI = "/api/..."`
- **Public endpoints** (`@AccessControl(skipRBAC = true)`): test WITHOUT `@WithMockUser` to verify unauthenticated access works
- **Constant sharing**: reuse constants from the controller via package-private `static final` + `static import` in the test — never duplicate literal values between source and test
- **Mock user has NO tenant membership by default.** `@WithMockUser` does not put the mock user in `users_tenants` unless asked to. Two ways to opt in, pick the narrower one:
  - The test already grants a specific tenant (e.g. via `TenantIsolationTestHelper` or `tenantRepository.addUserToTenant(...)`) → nothing else to do, that membership is what the endpoint should see.
  - The test hits an endpoint that attributes a tenant for writes (`TxCtx` + `TenantWriteScopeResolver`, typically behind `@RequireTenantSelector`) and does NOT otherwise grant any tenant → add `@WithMockUser(autoJoinDefaultTenant = true)` **on that one test method**, not at the class level.
  - Never flip it on at the class/suite level "to be safe": it silently turns a single-tenant caller into a two-tenant caller for any test in that class that also grants its own tenant (trips the "tenant scope already set for this transaction" guard on nested `@Transactional` calls), and defeats every test asserting a 400 on an ambiguous/missing tenant selector (e.g. `createWithoutSelectorIsRejected`) since the fallback selector no longer sees an unambiguous single tenant. This exact regression shipped once across several test classes at once — see `WithMockUser.autoJoinDefaultTenant()` javadoc.

### Tenant Isolation Tests (API)

- Use skill: [add-tenant-isolation-test](../../.claude/skills/add-test/TENANT_ISOLATION.md)

### Unscoped Reads and Wrong-Tenant Writes (PR Gate)

Every API shard of the pull request CI runs with `-Dopenaev.failclosed.detector=on
-Dopenaev.writeattr.detector=on`: a test fails when production code reads an active tenant table with
an empty scope (zero rows in production), or writes a `tenant_id` outside the request or job scope.
The failure names the site, its tables and the thread that ran the statement. Clear it in this order:

- Scope the test: seed fixtures inside `TenantScopedTransaction` with their tenant, and give the caller
  a membership (`TenantIsolationTestHelper`).
- `@ExpectsEmptyScopeReads("reason")` (`io.openaev.config`) only on a test whose subject is the empty
  answer of a caller or a path with no scope: it turns the fail-closed gate off for the whole test.
- `@WaivesUnscopedReads(value = "reason", reads = {"io.openaev.Foo.bar tables=t1,t2"})` on a test that
  asserts a positive outcome and cannot avoid the reads it names (a member of no tenant, a route that
  names no tenant). It waives those sites, for those tables, for that test only; an entry without
  `tables=` is refused.
- Each test carrying either annotation has its line in `test-scoped-read-waivers.txt`, whose size
  `TestScopedReadWaiversArchTest` freezes: a new annotation adds a line and raises `SIZE`.
- `failclosed-baseline.txt` and `writeattr-baseline.txt` waive a site for every test: the last resort,
  for a production read or write that is safe by design, with a comment giving the reason and the test
  that pins it.

## Integration Tests (Service)

- Extend `IntegrationTest` (same base as API tests)
- `@TestInstance(PER_CLASS) @Transactional` on the class
- `@WithMockUser` → `io.openaev.utils.mockUser.WithMockUser` (NOT `org.springframework`)
- `@Autowired` for the service under test and repositories
- Use **Fixtures** (`ExerciseFixture`, `InjectFixture`, `ScenarioFixture`, …) and **repositories** to set up real data
- Group with `@Nested` + `@DisplayName`
- Same `given_X_should_Y` naming and AAA pattern
- Prefer integration tests over unit tests for services that interact with the database

## Unit Tests (Service)

- Use only when the service has **no database interaction** (pure logic)
- `@ExtendWith(MockitoExtension.class)`
- `@Mock` for dependencies, `@InjectMocks` for the service under test
- Same `given_X_should_Y` naming and AAA pattern

## Fixtures

- Dedicated class per entity: `{Entity}Fixture`
- `createDefault{Entity}()` for unique names
- No inline data, no test duplication

## Composers

- `@Component`, extends `ComposerBase<{Entity}>`
- Call `.reset()` in `@BeforeEach`

## Dual-Scope Entity Tests

For entities with nullable `tenant_id` (Settings, User, Role, Group):

- **Isolation test**: create a platform entity (`tenant_id = null`) and a tenant entity (`tenant_id = X`); verify `PlatformXxxService.list()` returns only platform entries and `TenantXxxService.list(tenantId)` returns only tenant entries
- **Cross-tenant test**: create entities in tenant A and tenant B; verify `TenantXxxService.list(tenantA)` never returns tenant B data
- **Create scope test**: verify `PlatformXxxService.create()` always sets `tenant = null`; verify `TenantXxxService.create(tenantId)` always sets `tenant` to a non-null reference
- Test both APIs independently with appropriate `@WithMockUser` capabilities

## Frontend Tests (Vitest)

- **File location**: `openaev-front/src/__tests__/`, mirroring the source tree structure (e.g. source `src/utils/foo.ts` → test `src/__tests__/utils/foo.test.ts`)
- **File naming**: test file name must match the casing and format of the source file it tests (e.g. `url-helper.ts` → `url-helper.test.ts`, `Cron.ts` → `Cron.test.tsx`)
- Use `describe`, `expect`, `it` from `vitest`; use `vi` for mocks/spies
- Group related tests with nested `describe` blocks
- Use `describe.each` for parameterised tests over similar inputs
- **AAA pattern**: Arrange / Act / Assert (same as backend)
- Clean up shared state in `beforeEach` / `afterEach` (e.g. `localStorage.clear()`, `vi.restoreAllMocks()`)

### Design system components in unit tests

- A component rendering a library `Tooltip` must be wrapped in `<TooltipProvider>` (from `@filigran/design-system`) in the test — the app mounts it once in `index.tsx`
- Assert design-system adoption with the helpers of `src/__tests__/utils/designSystemAssertions.tsx`: `expectLibraryStyled` / `expectLibraryButton` / `expectLibraryIconButton` / `expectLibrarySearchField` (carries the library's state classes) and `expectNoMuiControls` (no `Mui*-` class except icon glyphs). Run them on a real subtree — a guard over mocked-out children asserts nothing, so also assert the subtree rendered something
- Accessibility via `@testing-library/jest-dom`: `toHaveAccessibleName` / `toHaveAccessibleDescription`, never `textContent` (it cannot tell "in the DOM" from "announced", e.g. inside an `aria-hidden` icon slot)
- jsdom does not apply the library stylesheet: assert **structure** (slots, classes, attributes), never computed style. Pixel values are measured in a real browser
- Library `TabsTrigger` activates on pointer down: fire `mouseDown`, not `click`

## Frontend E2E Tests (Playwright)

- Playwright for E2E: `yarn test:e2e`
- E2E config: `tests_e2e/`, fixtures in `tests_e2e/fixtures/`
- Locate by role and accessible name (`getByRole(..., { name, exact: true })` — the name match is a substring otherwise), not by `Mui*` classes: library fields have a different DOM. Field errors go through `MuiFormHelpers.getFieldError` (`tests_e2e/utils/MuiFormHelpers.ts`), which handles both MUI and library shapes
- E2E is the only gate that sees some regressions (e.g. a `<button>` that became a submit inside a form) — run the relevant specs when converting interactive components
- Import `test` from `tests_e2e/fixtures` (enforced by lint) and pick the user with `test.use({ persona })`. It defaults to admin; use `manager` or `observer` to verify permission rules (personas in `tests_e2e/utils/personas.ts`)
