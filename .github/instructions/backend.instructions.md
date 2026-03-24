---
applyTo: "openaev-api/src/main/java/**/*.java,openaev-model/src/main/java/**/*.java,openaev-framework/src/main/java/**/*.java"
description: "Backend Java/Spring conventions: entities, services, controllers, Hibernate, transactions"
---

# Backend Conventions

## ⚠️ Module Rule

> `openaev-framework` is deprecated — see [copilot-instructions.md](../copilot-instructions.md) for details. Never add new code there.

## Layering

- **Controller (API)** → depends only on **Service** — never inject a Repository in a controller
- Service → can call other Services and its own Repositories
- Repository → data access only, never called from controllers or utils
- Utils → static methods only, no state

## New Controllers (package `io.openaev.api.*`)

- `@RestController @RequestMapping("/api/{entities}") @RequiredArgsConstructor`
- Every endpoint: `@AccessControl` + `@LogExecutionTime` + `@Operation`
- URI: lowercase, hyphens, nouns — HTTP method defines the action
- Search: `@PostMapping("/search")`, Create: `201`, Delete: `204`
- Organize endpoints with section comments: `// -- CREATE --`, `// -- READ --`, `// -- UPDATE --`, `// -- DELETE --`
- **Never return JPA entities directly** from API endpoints — always use DTOs

## API DTOs, Mappers & Sub-resources

For each entity exposed via REST, create three files in the same `io.openaev.api.*` package:

- **`{Entity}Input.java`** — Java `record` for request body (`@JsonProperty`, `@NotBlank`, etc.)
- **`{Entity}Output.java`** — Java `record` for response body (all fields the client needs)
- **`{Entity}Mapper.java`** — Utility class with `private` constructor, static methods `toOutput(Entity)` and optionally `fromInput(String id, Input)`

```java
// DTOs — immutable Java record
public record {Entity}Input(
    @JsonProperty("entity_name") @NotBlank String name,
    @JsonProperty("entity_description") String description) {}

public record {Entity}Output(
    @JsonProperty("entity_id") @NotBlank String id,
    @JsonProperty("entity_name") @NotBlank String name,
    @JsonProperty("entity_description") String description) {}

// Mapper
public class {Entity}Mapper {
  private {Entity}Mapper() {}
  public static {Entity}Output toOutput({Entity} entity) { ... }
}

// Usage in controller (static import):
import static io.openaev.api.feature.{Entity}Mapper.toOutput;
public {Entity}Output findById(...) { return toOutput(service.findById(id)); }
public Page<{Entity}Output> search(...) { return service.search(input).map({Entity}Mapper::toOutput); }
```

## Entities

- Tenant-scoped: `TenantBase` + `@Filter("tenantFilter")` + `TenantBaseListener`
- Platform-level: `Base` only + `ModelBaseListener`
- Audit timestamps: implement `Auditable` + add `AuditableListener` (do **not** use Hibernate `@CreationTimestamp`/`@UpdateTimestamp`)
- Column: `{entity_singular}_{field}` → `@JsonProperty("same")`
- Tenant relation: always `@JsonIgnore`
- Collections: mutable (`new ArrayList<>()`) + `@Fetch(FetchMode.SUBSELECT)`

## Hibernate

- Collections must be mutable — never `List.of()` directly on entity fields
- Prefer unidirectional relationships
- `@Transactional` does NOT work on self-calls (Spring proxy bypass)
- Background tasks: explicit `@Transactional` (no OSIV outside controllers)
- `deleteById()` does a SELECT first — use native `@Query @Modifying` for perf-critical deletes

## Services

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- Read methods: `@Transactional(readOnly = true)`
- Always use `org.springframework.transaction.annotation.Transactional` — **never** `jakarta.transaction.Transactional` (which lacks `rollbackFor`, `readOnly`, etc.)
- Organize methods with section comments in this order: `// -- CREATE --`, `// -- READ --`, `// -- UPDATE --`, `// -- DELETE --`
- JavaDoc on all public methods (what + why)
- Fail fast: `Objects.requireNonNull()`, custom exceptions for business rules
- **Resolving associations from IDs**: use `ReferenceResolver.resolve(ids, Entity.class, repo::countByIdIn)` — never loop `findById()` (see `performance.instructions.md`)

## Repositories

- Use `JpaRepository` instead of `CrudRepository`
- Extend `JpaSpecificationExecutor` for entities that need search/filtering

## Lombok

- Services: `@RequiredArgsConstructor` + `private final` fields
- Entities: `@Getter @Setter` (not `@Data`)
- DTOs: `@Builder` OK, prefer records for new code
- Never `@Autowired` on fields in new code
