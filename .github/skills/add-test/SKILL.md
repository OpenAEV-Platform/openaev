---
name: add-test
description: >-
  Creates tests for an existing feature following OpenAEV patterns: fixture class,
  composer, integration test with @Nested groups, and optionally unit tests.
  Use when asked to add tests or improve test coverage.
---

# Add Tests

## Prerequisites

- Entity/feature to test
- Existing service and controller to test against

## Procedure

### Step 1 — Create or update the Fixture

Location: `openaev-api/src/test/java/io/openaev/utils/fixtures/files/`

```java
public class {Entity}Fixture {
  public static {Entity} createDefault{Entity}() {
    {Entity} entity = new {Entity}();
    entity.setName("{Entity}-" + RandomStringUtils.random(25, true, true));
    // set required fields
    return entity;
  }
}
```

### Step 2 — Create or update the Composer

Location: `openaev-api/src/test/java/io/openaev/utils/fixtures/composers/`

- Extend `ComposerBase<{Entity}>`
- Inner `Composer` with `persist()`, `delete()`, `get()`, `withId()`

### Step 3 — Create the Integration Test

Location: `openaev-api/src/test/java/io/openaev/rest/` or `api/`

Structure:
```java
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("{Feature} API tests")
public class {Feature}ApiTest extends IntegrationTest {
  public static final String URI = "/api/{features}";
  @Autowired private MockMvc mvc;
  @Autowired private {Feature}Composer composer;

  @BeforeEach void setup() { composer.reset(); }

  @Nested @WithMockUser(isAdmin = true) @DisplayName("CRUD operations")
  class CrudOperations {
    @Test @DisplayName("Can create") void given_validInput_should_createEntity() { /* Arrange / Act / Assert */ }
    @Test @DisplayName("Can read") void given_existingId_should_returnEntity() { ... }
    @Test @DisplayName("Can update") void given_existingEntity_should_updateSuccessfully() { ... }
    @Test @DisplayName("Can delete") void given_existingEntity_should_deleteSuccessfully() { ... }
    @Test @DisplayName("Can search") void given_searchInput_should_returnPage() { ... }
  }

  @Nested @WithMockUser(withCapabilities = {...}) @DisplayName("Permission checks")
  class PermissionChecks { ... }
}
```

### Step 4 — (Optional) Lightweight Controller Tests (no entity)

For controllers that don't manage entities (e.g. static endpoints, utility APIs, configuration endpoints):

- **Skip Steps 1 & 2** (no Fixture/Composer needed)
- **Reuse constants** from the controller via package-private static imports — never duplicate string literals
- **Test public endpoints** without `@WithMockUser` when `@AccessControl(skipRBAC = true)` is present
- **Test HTTP-level concerns**: status code, content-type, response body, custom headers, cache-control

```java
@TestInstance(PER_CLASS)
@DisplayName("{Feature} API tests")
class {Feature}ApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Nested @DisplayName("GET /endpoint")
  class EndpointGroup {

    @Test @DisplayName("Should respond without authentication")
    void given_unauthenticatedRequest_should_respondSuccessfully() throws Exception {
      // Arrange & Act
      var result = mvc.perform(get("/endpoint"));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
          .andExpect(content().string(EXPECTED_BODY))  // static import from controller
          .andExpect(header().string("Custom-Header", EXPECTED_VALUE));
    }
  }
}
```

**Key rules for lightweight tests:**
- Constants must be `static final` (package-private) in the controller, imported via `static import` in the test
- Group tests by endpoint using `@Nested` + `@DisplayName`
- Always test both authenticated and unauthenticated access when `@AccessControl(skipRBAC = true)`
- Test HTTP headers (Cache-Control, custom headers) when the controller sets them explicitly

### Step 5 — (Optional) Create Unit Test

For complex service logic:
```java
@ExtendWith(MockitoExtension.class)
class {Feature}ServiceUnitTest {
  @Mock private {Entity}Repository repository;
  @InjectMocks private {Feature}Service service;
  // given_X_should_Y naming + AAA (Arrange/Act/Assert) pattern
}
```

### Step 6 — Verify

```bash
mvn test -pl openaev-api -Dtest="{Feature}ApiTest"
mvn jacoco:check
```

## Sub-Skills

| Sub-Skill | Use when... |
|---|---|
| [TENANT_ISOLATION.md](TENANT_ISOLATION.md) | Adding tenant isolation tests (`@Nested TenantIsolation`) to verify cross-tenant data doesn't leak |



