---
applyTo: "openaev-api/src/main/java/**/*.java,openaev-model/src/main/java/**/*.java,openaev-framework/src/main/java/**/*.java"
description: "Backend Java/Spring conventions: entities, services, controllers, Hibernate, transactions"
---

# Backend Conventions

## ⚠️ Module Rule

> `openaev-framework` is deprecated — see [copilot-instructions.md](../copilot-instructions.md) for details. Never add new code there.

## ⚠️ Package Rule

> The `io.openaev.rest` package is **legacy** — it will be progressively migrated to `io.openaev.api`. **Never add new controllers, DTOs, or mappers in `rest/`**. All new API code goes in `io.openaev.api.*`.

## Scope of a Pull Request

- **No unused code**: every class, method, field and constant a PR adds has a caller in that PR. What a
  later step needs comes with that step.
- **No undecided concept**: no default value, option or notion nobody asked for (a "stable" channel, an
  `isOffered()` that also accepts `null`). Add it when the need is real.

## Layering

> Applies to code added or modified by a PR. Legacy code (`io.openaev.rest.*`, existing services) is not refactored retroactively.

| Layer | Does | Never |
|---|---|---|
| API (`io.openaev.api.*`) | validate (`@Valid`), map Input → entity/args, call **one** service method, map result → Output | inject a repository, hold a business rule, catch or translate an exception, chain service calls with logic in between |
| Service | business rules, transactions; calls other services and **its own** repository | return an `*Output`, build HTTP (`ResponseEntity`, `ResponseStatusException`, servlet types), inject another aggregate's repository |
| Repository | data access | be injected outside its owning service |
| Entity | state + JPA mapping | business logic (see Entities) |
| Utils | static methods | hold state |

- Own repository = the aggregate the service is named after (`ScenarioService` → `ScenarioRepository`).
  Need another aggregate's data → call its service. Child entities with no service of their own
  (join tables, e.g. `ScenarioTeamUser`) belong to the parent aggregate's service.
- An endpoint needing several service calls is a missing service method: add it to the service, the controller calls it once.
- A service never returns an `*Output`: it returns entities, and the API layer maps them with `{Entity}Mapper` (see API DTOs, Mappers).
- Errors: services throw an existing domain exception from `io.openaev.rest.exception` (`ElementNotFoundException`,
  `BadRequestException`, `ConflictException`…). The HTTP status is decided only in `RestBehavior` (`@RestControllerAdvice`):
  never `ResponseStatusException`, never a try/catch in a controller to translate an exception. A new exception type gets
  its `@ExceptionHandler` in `RestBehavior`.
- **Repository methods used by other services must be exposed through the owning service.** A repository is a private implementation detail of its associated service. If another service needs data from that repository, the owning service must expose a pass-through method — the consuming service must never inject the repository directly. When adding a repository method intended for cross-service use, always add the corresponding service method in the same commit.

## New Controllers (package `io.openaev.api.*`)

- `@RestController @RequestMapping("/api/{entities}") @RequiredArgsConstructor`
- Tenant-scoped data: routes on `TENANT_PREFIX + "/{entities}"` only, no `/api/{entities}` alias (see multi-tenancy rule 9)
- Every endpoint: `@AccessControl` + `@Operation`; `@LogExecutionTime` only on endpoints at risk of being slow (heavy queries, imports, exports)
- Preview feature: check it in the endpoint with `PreviewFeatureService.isFeatureEnabled(...)` and throw
  `ElementNotFoundException` (404) when it is off; no Spring `@Conditional` reading the properties
- URI: lowercase, hyphens, nouns — HTTP method defines the action
- Search: `@PostMapping("/search")`, Create: `201`, Delete: `204`
- Organize endpoints with section comments: `// -- CREATE --`, `// -- READ --`, `// -- UPDATE --`, `// -- DELETE --`
- **Never return JPA entities directly** from API endpoints — always use DTOs

## API DTOs, Mappers & Sub-resources

For each entity exposed via REST, create these files in `io.openaev.api.{feature}`:

- **`dto/{Entity}Input.java`** — Java `record` for request body (`@JsonProperty`, `@NotBlank`, etc.)
- **`dto/{Entity}Output.java`** — Java `record` for response body (see the output rules in `api.instructions.md`)
- **`{Entity}Mapper.java`** — Utility class with `private` constructor, static methods `toOutput(Entity)` and optionally `fromInput(String id, Input)`. A `@Component` only when it needs another bean (e.g. `ObjectMapper`)

Copying an input into an entity, or an entity into an output, belongs in the mapper, never in a service.

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
- Dual-scope (Settings, User, Role, Group): `DualScopeBase` + `ModelBaseListener` only — no `@Filter`, nullable `tenant_id`, see `multi-tenancy.instructions.md`
- Platform-level: `Base` only + `ModelBaseListener`
- Audit timestamps: implement `Auditable` + add `AuditableListener` (do **not** use Hibernate `@CreationTimestamp`/`@UpdateTimestamp`)
- Column: `{entity_singular}_{field}` → `@JsonProperty("same")`
- Tenant relation: always `@JsonIgnore`
- Collections: mutable (`new ArrayList<>()`) + `@Fetch(FetchMode.SUBSELECT)`
- Several kinds of one thing with their own fields: one table, one class per kind
  (`@Inheritance(SINGLE_TABLE)`, as `Asset` / `Endpoint`), never one class whose fields only apply to some rows
- **No business logic in entities**: fields, JPA mapping, accessors, collection plumbing (`clear()` + `addAll()`),
  lifecycle callbacks, `equals`/`hashCode`. A one-expression getter on the entity's own fields is fine (`isDeleted()`).
  No computed `@JsonProperty` getter, stream, branching, state machine or static factory: rules go in the service,
  display values in the mapper. Existing ones (`Inject`, `InjectorContract`, `Exercise`, `User`…) are legacy kept for
  `rest/` endpoints returning entities: don't add any, don't extend them.

## Hibernate

- Collections must be mutable — never `List.of()` directly on entity fields
- Prefer unidirectional relationships
- `@Transactional` does NOT work on self-calls (Spring proxy bypass)
- Background tasks: explicit `@Transactional` (no OSIV outside controllers)
- `deleteById()` does a SELECT first — use native `@Query @Modifying` for perf-critical deletes, but only when the entity is not `@Indexable`, `@AuditDiffTracked`, or streamed: a native or bulk delete skips the listener chain, so the search index, audit log, and activity stream are not updated (see `orm.instructions.md`)
- **Never mutate a managed entity in a read path — `readOnly = true` does NOT make it safe**:
  with OSIV the Hibernate session outlives the controller's read-only transaction, and any later
  read-write transaction on the same thread (e.g. the spring-session JDBC save at response commit)
  flushes the dirtied entity. If the row was deleted/changed in between, the flush fails with
  `StaleStateException` and the GET returns a 500 (issue #7092, regression of #6469). Resolve
  display-only values into the output DTO (pass them as mapper parameters) instead of calling
  setters on managed entities.

## Services

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- Read methods: `@Transactional(readOnly = true)`
- Always use `org.springframework.transaction.annotation.Transactional` — **never** `jakarta.transaction.Transactional` (which lacks `rollbackFor`, `readOnly`, etc.)
- Organize methods with section comments in this order: `// -- LIST --`, `// -- SEARCH --`, `// -- CREATE --`, `// -- UPDATE --`, `// -- DELETE --`, `// -- OPTIONS --`
- Fail fast: `Objects.requireNonNull()`, custom exceptions for business rules
- **Resolving associations from IDs**: use `ReferenceResolver.resolve(ids, Entity.class, repo::countByIdIn)` — never loop `findById()` (see `performance.instructions.md`)
- **One role per service**: split a service whose methods have different jobs or transaction rules
  (check files outside a transaction, write in one, list read-only)
- **One aggregate per service**: a new use case that mainly orchestrates several aggregates (export, import,
  conversion, duplication) goes in a service named after the use case (`ScenarioToExerciseService`), not in an
  existing aggregate service
- **A long method reads like a recipe**: named private steps (`latestRelease`, `refuseIfOlder`, `saveItems`),
  plain loops; no generic `Supplier`/`Consumer` machinery to save a few lines
- **A rule that depends on a type lives on its enum** (e.g. the resource an event type is limited to), not
  in a chain of `if` in a service
- **Notifications go through the notification engine**: add the resource to `NotificationResourceCatalog`
  and the event to `NotificationTriggerEventType`, users subscribe; never write alerts to users directly

## Repositories

- Use `JpaRepository` instead of `CrudRepository`
- Extend `JpaSpecificationExecutor` for entities that need search/filtering

## Lombok

- Services: `@RequiredArgsConstructor` + `private final` fields
- Entities: `@Getter @Setter` (not `@Data`)
- DTOs: `@Builder` OK, prefer records for new code
- Never `@Autowired` on fields in new code

## Formatting (Spotless)

- **After editing Java files**, run `.\scripts\hooks\format-java.ps1` (Windows) or `./scripts/hooks/format-java.sh` (Unix). Output: `OK` = success.

## Code Comments

- **Don't comment the obvious**: never add a comment that merely restates what the code already
  makes clear (e.g. `// Safe: newConnector is transient, not managed by Hibernate` when it's
  created with `new Connector()`). Comments should explain *why*, not *what*.
- **Javadoc only for what the signature does not say**: side effects, thrown exceptions, non-obvious behaviour.
  Never a sentence that paraphrases the method name (`/** Updates a tenant. */`). Same rule for private methods: most need none.
- **Don't duplicate computed values**: compute once (e.g. `String logoFilename = getLogoFilename()`)
  and pass as an argument — never repeat the same `.formatted(...)` expression in multiple places.
