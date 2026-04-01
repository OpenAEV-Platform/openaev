---
applyTo: "**/*Test.java,**/*Test*.java,**/test/**,**/*.test.*,**/*.spec.*"
description: "Testing conventions: integration tests, unit tests, fixtures, composers, assertions"
---

# Testing Conventions

> Templates: see `prompts/new-test.prompt.md`. Reference examples: search for `GroupApiTest.java` or `RoleApiTest.java`.

## Integration Tests (API)

- Extend `IntegrationTest`
- `@TestInstance(PER_CLASS) @Transactional` on the class
- `@WithMockUser` → `io.openaev.utils.mockUser.WithMockUser` (NOT `org.springframework`)
- Group with `@Nested` + `@DisplayName`
- **Method naming**: `given_X_should_Y` → e.g. `given_validInput_should_createGroup()`, `given_crowdstrike_should_not_LaunchAtomicTesting()`
- **AAA pattern**: `// Arrange` / `// Act` / `// Assert`
- JSON: `assertThatJson(response).node("field").isEqualTo(...)` (json-unit library)
- URI constant at class level: `public static final String FEATURE_URI = "/api/..."`

## Unit Tests (Service)

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