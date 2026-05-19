You are writing tests for OpenAEV.

> Follow conventions from `testing.instructions.md`.

## Integration test template

```java
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("{Feature} API tests")
public class {Feature}ApiTest extends IntegrationTest {

  public static final String FEATURE_URI = "/api/{features}";

  @Autowired private MockMvc mvc;
  @Autowired private {Feature}Composer {feature}Composer;

  @BeforeEach
  void setup() {
    {feature}Composer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Normal CRUD")
  class NormalCRUD {

    @Test
    @DisplayName("Can create a {feature} with input")
    void given_validInput_should_createFeature() throws Exception {
      // Arrange
      {Feature}Input input = new {Feature}Input(...);
      // Act
      String response = mvc.perform(
              post(FEATURE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn().getResponse().getContentAsString();
      // Assert
      assertThatJson(response).node("{feature}_name").isEqualTo(input.getName());
    }

    @Test
    @DisplayName("Can update a {feature}")
    void given_existingFeature_should_updateSuccessfully() throws Exception { ... }
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
  @DisplayName("With restricted capabilities")
  class WithRestrictedCapabilities {
    @Test
    @DisplayName("Can read but cannot delete")
    void given_restrictedCapabilities_should_not_allowDelete() throws Exception { ... }
  }
}
```

## Unit test template

```java
@ExtendWith(MockitoExtension.class)
class {Feature}ServiceUnitTest {
  @Mock private {Feature}Repository repository;
  @InjectMocks private {Feature}Service service;

  @Test
  @DisplayName("Should find by id")
  void given_existingId_should_returnEntity() {
    // Arrange
    when(repository.findById(any())).thenReturn(Optional.of(fixture));
    // Act
    var result = service.findById("id");
    // Assert
    assertThat(result).isNotNull();
  }
}
```

## Composer pattern

Composers support fluent builder methods like `.withId()`:

```java
@Component
public class {Feature}Composer extends ComposerBase<{Entity}> {
  @Autowired private {Entity}Repository repository;

  public class Composer extends InnerComposerBase<{Entity}> {
    private final {Entity} entity;
    public Composer({Entity} entity) { this.entity = entity; }

    public Composer withId(String id) {
      this.entity.setId(id);
      return this;
    }

    @Override public Composer persist() { repository.save(entity); return this; }
    @Override public Composer delete() { repository.delete(entity); return this; }
    @Override public {Entity} get() { return this.entity; }
  }

  public Composer for{Entity}({Entity} entity) {
    generatedItems.add(entity);
    return new Composer(entity);
  }
}
```

## Fixture pattern

```java
public class {Feature}Fixture {
  public static {Entity} createDefault{Entity}() {
    {Entity} entity = new {Entity}();
    entity.setName("{Entity}-" + RandomStringUtils.random(25, true, true));
    return entity;
  }
}
```
```








