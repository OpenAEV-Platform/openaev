---
applyTo: "openaev-api/src/main/java/io/openaev/api/**,openaev-api/src/main/java/io/openaev/rest/**"
description: "API layer conventions: controller structure, DTOs, pagination, error handling, OpenAPI"
---

# API Layer Conventions

## DTO Rules

- **Never expose JPA entities in API responses** — always use Output DTOs
- Separate `CreateInput` / `UpdateInput` from `Output` when fields differ
- Use `@Valid` on all `@RequestBody` parameters
- Use `@JsonIgnore` on any field that should not be serialized (`tenant_id`, internal relations)
- **Outputs start minimal**: only the fields a screen or client reads today; add fields with the screen
  that needs them
- **One output per kind**: no shared output whose fields stay empty for some kinds
- JSON names: `<object>_<field>` (`catalog_payload_name`); a result summary may use plain names
  (`added`, `refused`)
- A field another system reads is a contract: keep it, and never send `null` where it expects a string.
  Removing, renaming or retyping an endpoint URL or method, a required input field, an output field or a
  response status is a breaking change: it needs a migration plan
- Inputs hold plain values and ids, never a JPA entity
- An id list in an output is annotated `@ArraySchema(schema = @Schema(type = "string"))`

## Responses

- A controller method returns the output directly; a status other than 200 goes in `@ResponseStatus`
  (`CREATED`, `NO_CONTENT`)
- `ResponseEntity` only when the status or headers depend on the request

## Pagination

- Search endpoints return `Page<T>`, never `List<T>`
- Accept `SearchPaginationInput` — Spring resolves page, size, sorts, filters
- List endpoints (`GET /api/{entities}`) may return `List<T>` for small reference data only
- Options endpoints return `List<FilterUtilsJpa.Option>` for autocomplete dropdowns

## Validation

- Use Bean Validation annotations: `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Pattern`
- Custom validation via `@Valid` + validator classes for complex business rules

## OpenAPI

- `@Operation` `summary`: 60 characters at most, plain words
- `description` only when the summary is not enough: one sentence, 120 characters at most
- `@ApiResponse` for each status the endpoint returns; complex fields documented with `@Schema`

## Anti-Patterns

- ❌ Business logic in controllers — controllers only validate input, call service, map output
- ❌ Catching exceptions in controllers — let `@ControllerAdvice` handle them
- ❌ Injecting `Repository` in controllers — always go through `Service`
- ❌ Returning JPA entities directly from endpoints — always map to Output DTOs
- ❌ Missing `@AccessControl` on any endpoint — every endpoint must be protected

