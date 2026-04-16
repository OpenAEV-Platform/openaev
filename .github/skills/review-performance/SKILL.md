---
name: review-performance
description: >-
  Performance review checklist for OpenAEV code: N+1 queries, fetch strategy,
  pagination, indexing, memory usage. Use when reviewing PRs or auditing performance of a feature.
---

# Performance Review

## Procedure

### Step 1 — Check for N+1 queries

- Search for loops that call the database:
  ```bash
  grep -rn "\.findById\|\.findAll\|\.existsById" openaev-api/src/main/java/ --include="*.java"
  ```
- Verify that no `findById()` or `findAll()` is called inside a `for` / `forEach` / `stream().map()`
- If a loop needs related entities, prefer `findAllById()` or a single `@Query` with `IN` clause
- Check `@ManyToMany` / `@OneToMany` collections have `@Fetch(FetchMode.SUBSELECT)` to avoid N+1

### Step 2 — Check fetch strategy

- All associations should default to `FetchType.LAZY`
- `FetchType.EAGER` is only acceptable for small, always-needed collections (e.g. capabilities)
- Search for EAGER on potentially large collections:
  ```bash
  grep -rn "FetchType.EAGER" openaev-model/src/main/java/ --include="*.java"
  ```
- Verify that LAZY collections are never accessed outside a transaction (causes `LazyInitializationException`)
- For API endpoints returning IDs only: LAZY + subselect is preferred

### Step 3 — Check pagination

- All list/search REST endpoints MUST return `Page<T>`, not unbounded `List<T>`
- Search for endpoints returning lists:
  ```bash
  grep -rn "List<.*>" openaev-api/src/main/java/io/openaev/api/ --include="*.java" | grep -i "public\|return"
  ```
- Verify reasonable default page size (10-20) and max page size (100)
- `findAll()` without pagination is only acceptable for small reference data tables

### Step 4 — Check query efficiency

- Search for `findAll()` that could be filtered at DB level:
  ```bash
  grep -rn "\.findAll()" openaev-api/src/main/java/ --include="*.java"
  ```
- Verify existence checks use `existsById()` instead of `findById().isPresent()`
- Verify bulk deletes use `@Modifying @Query` instead of loading + deleting one by one
- Check that `@Transactional(readOnly = true)` is used on all read methods

### Step 5 — Check database indexing

- New columns used in WHERE / ORDER BY / JOIN should have indexes
- FK columns in join tables should be indexed (composite PK covers one direction, check the other)
- For new migrations, verify:
  ```bash
  grep -rn "CREATE TABLE\|CREATE INDEX\|ADD COLUMN" openaev-api/src/main/java/io/openaev/migration/ --include="*.java"
  ```

### Step 6 — Check memory usage

- No large `byte[]` or full file content loaded in memory — use streaming
- No unbounded in-memory collections (e.g. `findAll()` result stored in a `List`)
- Search for potential memory issues:
  ```bash
  grep -rn "byte\[\]\|ByteArrayOutputStream\|toByteArray" openaev-api/src/main/java/ --include="*.java"
  ```

### Step 7 — Check transaction scope

- Read-only operations use `@Transactional(readOnly = true)`
- No long-running computation inside `@Transactional` (keep transactions short)
- Verify no `@Transactional` self-calls (Spring proxy bypass):
  ```bash
  grep -rn "this\." openaev-api/src/main/java/io/openaev/service/ --include="*.java" | grep -i "find\|get\|search\|create\|update\|delete"
  ```

### Step 8 — Report

Document findings using conventional comments format:
- `issue (blocking):` for performance bugs (N+1, missing pagination, unbounded queries)
- `suggestion (non-blocking):` for optimizations (index, fetch strategy, caching)
- `note:` for informational items (acceptable tradeoffs, future improvements)

