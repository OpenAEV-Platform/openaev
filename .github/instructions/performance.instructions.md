---
applyTo: "openaev-api/src/main/java/**/*.java,openaev-model/src/main/java/**/*.java"
description: "Performance conventions: N+1 queries, lazy/eager loading, pagination, caching, indexing"
---

# Performance Conventions

## JPA & Hibernate

### N+1 Queries

- **Never** iterate a collection to call the DB in a loop — batch or join fetch instead
- Use `@Fetch(FetchMode.SUBSELECT)` on `@ManyToMany` / `@OneToMany` collections to avoid N+1
- Prefer `@Transactional(readOnly = true)` on read methods — disables dirty checking

### Fetch Strategy

- Default to `FetchType.LAZY` for all associations — only load what's needed
- `FetchType.EAGER` is acceptable only for small, always-needed collections (e.g. capabilities on a role)
- Never use `FetchType.EAGER` on collections that can grow unbounded
- For APIs returning IDs only (serialized via `MultiIdListSerializer`): LAZY + `@Fetch(FetchMode.SUBSELECT)` is enough

### Resolving Entity Associations from IDs

When a service receives a list of entity IDs to set as associations (e.g. role IDs on a group):
- **Never** loop with `findById()` — that's N SELECT queries
- **Use `ReferenceResolver`** (`io.openaev.utils.ReferenceResolver`):
  - 1 `COUNT` query to validate all IDs exist
  - 0 `SELECT` queries to build proxies via `EntityManager.getReference()`
  - Throws `EntityNotFoundException` with a clear message if any ID is invalid

```java
// ✅ Good — 2 queries total (1 count roles + 1 count users)
group.setPlatformRoles(
    referenceResolver.resolve(roleIds, PlatformRole.class, roleRepo::countByIdIn));
group.setUsers(
    referenceResolver.resolve(userIds, User.class, userRepo::countByIdIn));

// ❌ Bad — N queries (1 SELECT per ID)
roleIds.stream().map(roleService::findById).toList();
```

Repositories that are used with `ReferenceResolver` must expose a `countByIdIn(Set<String>)` method.

### Pagination

- All list/search endpoints MUST use pagination (`Page<T>`)
- Never return unbounded `List<T>` from an API endpoint (except for small reference data)
- Use `PaginationUtils.buildPaginationJPA()` for standard search endpoints
- Set reasonable default page size (10-20), max page size (100)

### Queries

- Prefer repository methods or `@Query` JPQL over `CrudRepository.findAll()` when filtering
- Use `existsById()` instead of `findById().isPresent()` for existence checks
- Use `@Modifying @Query` for bulk updates/deletes — avoid loading entities just to delete them
- Use projections (DTO queries) for read-heavy endpoints that don't need the full entity
- Add database indexes on columns used in WHERE, ORDER BY, and JOIN conditions

## Collections & Streams

- Never materialize large collections in memory — stream and filter at the DB level
- Use `Set` instead of `List` when order doesn't matter and uniqueness is required

## REST API

- Large responses: always paginate
- Search endpoints: support filtering at DB level, not in Java
- Avoid returning deep object graphs — use DTOs with IDs, let the client fetch related entities
- File downloads: use streaming (`StreamingResponseBody`), not `byte[]` in memory

## Anti-Patterns to Avoid

- ❌ Loading all entities to count them — use `repository.count()` or `COUNT()` query
- ❌ `findAll()` without pagination on tables that can grow
- ❌ Iterating a list to call `repository.findById()` in a loop — use `ReferenceResolver` or `findAllById()` instead
- ❌ Opening a transaction for read-only operations without `readOnly = true`
- ❌ Returning JPA entities with LAZY collections from `@RestController` (triggers proxy outside session)

