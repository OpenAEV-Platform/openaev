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

## Frontend Tests

- Vitest for unit tests: `yarn test`
- Playwright for E2E: `yarn test:e2e`
- E2E config: `tests_e2e/`, fixtures in `tests_e2e/fixtures/`
