---
applyTo: "**/*Test.java,**/*Test*.java,**/test/**,**/*.test.*,**/*.spec.*,**/tests_e2e/**"
description: "Testing conventions: integration tests, unit tests, fixtures, composers, assertions"
---

# Testing Conventions

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

- Use skill: [add-tenant-isolation-test](../skills/add-test/TENANT_ISOLATION.md)

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

## Frontend E2E Tests (Playwright)

- Playwright for E2E: `yarn test:e2e`
- E2E config: `tests_e2e/`, fixtures in `tests_e2e/fixtures/`
