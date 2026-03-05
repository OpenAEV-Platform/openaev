You are adding a new REST API endpoint to OpenAEV.

> Follow conventions from `backend.instructions.md` and `security.instructions.md`.

## Use the NEW style (package `io.openaev.api.*`)

Old style (`io.openaev.rest.*` extending `RestBehavior`) is legacy — do not use for new features.

## Controller template

```java
@RestController
@RequestMapping("/api/{entity-plural}")
@RequiredArgsConstructor
public class {Entity}Api {
  private final {Entity}Service service;

  // -- CREATE --
  @Operation(summary = "Create ...", description = "...")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "201", description = "Created"),
      @ApiResponse(responseCode = "400", description = "Invalid input")
  })
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.XXX)
  @LogExecutionTime
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public {Entity}Output create(@Valid @RequestBody {Entity}Input input) { ... }

  // -- READ --
  @AccessControl(resourceId = "#entityId", actionPerformed = Action.READ, resourceType = ResourceType.XXX)
  @LogExecutionTime
  @GetMapping("/{entityId}")
  public {Entity}Output getById(@PathVariable String entityId) { ... }

  // -- SEARCH --
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.XXX)
  @LogExecutionTime
  @PostMapping("/search")
  public Page<{Entity}Output> search(@RequestBody @Valid SearchPaginationInput input) { ... }

  // -- UPDATE --
  @AccessControl(resourceId = "#entityId", actionPerformed = Action.WRITE, resourceType = ResourceType.XXX)
  @LogExecutionTime
  @PutMapping("/{entityId}")
  public {Entity}Output update(@PathVariable String entityId, @Valid @RequestBody {Entity}Input input) { ... }

  // -- DELETE --
  @AccessControl(resourceId = "#entityId", actionPerformed = Action.DELETE, resourceType = ResourceType.XXX)
  @LogExecutionTime
  @DeleteMapping("/{entityId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String entityId) { ... }
}
```

## DTOs — immutable Java `record`

```java
public record {Entity}Input(
    @JsonProperty("entity_name") @NotBlank String name,
    @JsonProperty("entity_description") String description) {}

public record {Entity}Output(
    @JsonProperty("entity_id") @NotBlank String id,
    @JsonProperty("entity_name") @NotBlank String name,
    @JsonProperty("entity_description") String description) {}
```

## Checklist

- [ ] `@AccessControl` + `@LogExecutionTime` + `@Operation` on every endpoint
- [ ] DTOs (records) for input/output + Mapper class
- [ ] No business logic in controller




