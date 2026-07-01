---
applyTo: "openaev-api/src/main/java/**/*.java,openaev-model/src/main/java/**/*.java,openaev-framework/src/main/java/**/*.java"
description: "Backend Java/Spring conventions: entities, services, controllers, Hibernate, transactions"
---

# Backend Conventions

## ⚠️ Module Rule

> `openaev-framework` is deprecated — see [copilot-instructions.md](../copilot-instructions.md) for details. Never add new code there.

## ⚠️ Package Rule

> The `io.openaev.rest` package is **legacy** — it will be progressively migrated to `io.openaev.api`. **Never add new controllers, DTOs, or mappers in `rest/`**. All new API code goes in `io.openaev.api.*`.

## Layering

- **Controller (API)** → depends only on **Service** — never inject a Repository in a controller
- Service → can call other Services and its own Repositories
- Repository → data access only, never called from controllers or utils
- **Repository methods used by other services must be exposed through the owning service.** A repository is a private implementation detail of its associated service. If another service needs data from that repository, the owning service must expose a pass-through method — the consuming service must never inject the repository directly. When adding a repository method intended for cross-service use, always add the corresponding service method in the same commit.
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
- Dual-scope (Settings, User, Role, Group): `DualScopeBase` + `ModelBaseListener` only — no `@Filter`, nullable `tenant_id`, see `multi-tenancy.instructions.md`
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
- **`@Transactional(readOnly = true)` permits safe entity mutation**: Hibernate disables dirty-check
  in read-only sessions, so setting a transient display field on a managed entity (e.g.
  `connector.setName(resolved)`) will never flush to DB. Prefer this over threading an extra
  parameter through abstract method signatures and mapper call chains.

## Services

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- Read methods: `@Transactional(readOnly = true)`
- Always use `org.springframework.transaction.annotation.Transactional` — **never** `jakarta.transaction.Transactional` (which lacks `rollbackFor`, `readOnly`, etc.)
- Organize methods with section comments in this order: `// -- LIST --`, `// -- SEARCH --`, `// -- CREATE --`, `// -- UPDATE --`, `// -- DELETE --`, `// -- OPTIONS --`
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

## Formatting (Spotless)

- **After editing Java files**, run `.\scripts\hooks\format-java.ps1` (Windows) or `./scripts/hooks/format-java.sh` (Unix). Output: `OK` = success.

## Code Comments

- **Don't comment the obvious**: never add a comment that merely restates what the code already
  makes clear (e.g. `// Safe: newConnector is transient, not managed by Hibernate` when it's
  created with `new Connector()`). Comments should explain *why*, not *what*.
- **Don't duplicate computed values**: compute once (e.g. `String logoFilename = getLogoFilename()`)
  and pass as an argument — never repeat the same `.formatted(...)` expression in multiple places.
## Tanium Executor Scripts

When adding or modifying Tanium package scripts (`.sh`, `.ps1`) and their JSON package definition:

### Fail-fast on missing argument
Both `.sh` and `.ps1` scripts must **fail fast** with `exit 1` + explicit error message when the payload argument is missing.
- **✅ Correct (`.sh`)**: `if [ -z "${1:-}" ]; then echo "[ERROR] no payload argument" >&2; exit 1; fi`
- **✅ Correct (`.ps1`)**: `if(-not $arg) { Write-Host "[ERROR] No payload argument supplied"; exit 1 }`
- **❌ Wrong**: silent NOOP substitution (`$arg = "NOOP"`) that masks the error and reports success

### Post-launch verification
After submitting the payload, confirm it actually started (not just "accepted"):
- **nohup/setsid**: capture launcher PID, `sleep 1`, then verify with `kill -0 $PID` or `wait`
- **systemd-run**: use `--wait` flag so the call blocks until the transient unit exits (real exit code)
- **`at` / fire-and-forget**: acceptable when the scheduler provides no status API

### Package JSON integrity
Every file entry in the Tanium package JSON must include `hash` (SHA-256 hex) and `size` (bytes) fields, matching the actual script content. **Recompute hash and size after any script change.**
```json
{ "hash": "<sha256>", "name": "openaev-ttr.sh", "source": "", "size": 4648, ... }
```
