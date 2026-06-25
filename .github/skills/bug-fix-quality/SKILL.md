---
name: bug-fix-quality
description: >-
  Post-fix quality checklist for bug fixes. Ensures the fix is complete, well-tested,
  retrocompatible, and follows OpenAEV conventions. Run after writing a bug fix and before
  opening or updating a PR. Captures lessons from real review iterations.
---

# Bug Fix Quality Checklist

## When to Use

Run this checklist **after** completing a bug fix and **before** pushing or requesting review.
It catches common issues that reviewers flag — avoiding unnecessary review round-trips.

## Procedure

### Step 1 — All CRUD Paths Covered

> **Lesson**: When a bug is in `createX()`, the same logic is likely missing from `updateX()` and `upsertX()`.

- [ ] Identify **all service methods** that write the affected field(s)
- [ ] Verify the fix is applied in **every** write path (create, update, upsert, import, sync)
- [ ] If a private helper was created, verify it's called from all relevant methods

```bash
# Find all methods that touch the field
grep -rn "set{FieldName}\|{fieldName} =" openaev-api/src/main/java/ --include="*.java"
```

### Step 2 — Centralize Repeated Logic

> **Lesson**: When the same pattern appears in 3+ methods, extract a private helper.
> Reviewers will ask for a "sanity check" or "generic" approach instead of inline duplication.

- [ ] If the fix logic appears in more than one method → extract a `private` helper
- [ ] Name it descriptively: `ensureX()`, `sanitizeX()`, `validateX()` — not `fixBug()`
- [ ] Place the helper at the **bottom** of the service class (before inner classes)
- [ ] Each call site should be a single line: `ensureSeenIp(entity);`

**Anti-pattern** (don't do this):
```java
// In createEndpoint:
if (endpoint.getSeenIp() == null && endpoint.getIps() != null && !endpoint.getIps().isEmpty()) {
  endpoint.setSeenIp(endpoint.getIps().getFirst());
}
// Same block copy-pasted in updateEndpoint and upsertEndpoint
```

**Correct pattern**:
```java
private void ensureSeenIp(Endpoint endpoint) {
  if (endpoint.getSeenIp() == null && endpoint.getIps() != null && !endpoint.getIps().isEmpty()) {
    endpoint.setSeenIp(endpoint.getIps().getFirst());
  }
}
```

### Step 3 — Service-Level vs Model-Level

> **Lesson**: Don't put derived field logic in entity getters/setters when `BeanUtils.copyProperties`
> is used in the service layer. The setter would receive raw, potentially null values from the DTO,
> bypassing any sanitization.

- [ ] Check if the entity is populated via `BeanUtils.copyProperties(input, entity)`
- [ ] If yes → derived field logic **must** live in the service, not the entity
- [ ] If the entity has setters that transform values (e.g., `hostname.toLowerCase()`), ensure
      callers always provide non-null values or the setter handles null gracefully

### Step 4 — Retrocompatibility & Data Migration

> **Lesson**: A service-level fix only handles **future** operations. Existing data with the bug
> remains broken unless a migration backfills it.

- [ ] Does the bug affect **existing persisted data**?
- [ ] If yes → add a Flyway migration to backfill (follow `migration.instructions.md`)
- [ ] If adding a NOT NULL constraint: ensure all existing rows have a value first
- [ ] Consider: is a NOT NULL constraint appropriate, or should the field remain nullable?
  - If the field can legitimately be null in some scenarios → keep nullable + service-level enforcement
  - If the field must always have a value → add migration backfill + then NOT NULL constraint

### Step 5 — Test Integration (Not Isolation)

> **Lesson**: Don't create a separate `@Nested` class just for a bug fix.
> Instead, enrich existing tests. This produces fewer tests, better coverage, and less code.

- [ ] **DO**: Add assertions to existing tests that exercise the affected code path
- [ ] **DO**: Add edge cases (null inputs, empty arrays) to the existing `@Nested` group
- [ ] **DON'T**: Create a `@Nested AgentlessBugXXX` class — it will be rejected in review

**What to add to existing tests**:
1. **Main path assertion**: In the existing "happy path" test, add an assertion for the fixed field
   ```java
   // In existing shouldCreateEndpointFromInput test:
   assertThat(result.getSeenIp()).isEqualTo("192.168.1.1");
   ```
2. **Edge cases**: Add new test methods to the **existing** `@Nested` group
   ```java
   // In existing @Nested CreateEndpoint group:
   @Test @DisplayName("Should leave seenIp null when no IPs provided")
   void given_inputWithoutIps_should_leaveSeenIpNull() { ... }
   ```
3. **All CRUD paths**: If you fixed create + update + upsert, ensure tests exist for each path

### Step 6 — BeanUtils.copyProperties Pitfalls

> **Lesson**: `BeanUtils.copyProperties(source, target)` copies ALL matching fields — including
> null values. If the entity's setter does a transformation (e.g., `hostname.toLowerCase()`),
> copying a null value causes NPE.

- [ ] Check all entity setters for null-unsafe transformations:
  ```bash
  grep -A3 "public void set" openaev-model/src/main/java/io/openaev/database/model/{Entity}.java | grep -B1 "toLowerCase\|toUpperCase\|trim\|substring"
  ```
- [ ] In tests that use `BeanUtils.copyProperties` (via `setUpdateAttributes` or similar),
      **set all fields** that have null-unsafe setters — even if they're not relevant to the test
- [ ] Consider adding null checks to entity setters if they do transformations:
  ```java
  public void setHostname(String hostname) {
    this.hostname = hostname != null ? hostname.toLowerCase() : null;
  }
  ```

### Step 7 — Formatting & CI Readiness

> **Lesson**: Don't waste review cycles on formatting failures. Check before pushing.

- [ ] Run `mvn spotless:check -pl openaev-api` (or `mvn spotless:apply` to auto-fix)
- [ ] Google Java Format enforces **100-character line limit** — common violations:
  - `when(repo.findByX("long-arg", TenantContext.getCurrentTenant()))` → needs line break
  - Multi-line `if` conditions that fit on one line → must be on one line
- [ ] Run `mvn test -pl openaev-api -Dtest="{AffectedTest}"` to verify tests pass
- [ ] Stage only relevant files: `git add <specific-files>` — **never** `git add -A`

### Step 8 — Self-Review Checklist

Before pushing, mentally review as if you were the reviewer:

- [ ] Is the fix **minimal**? No drive-by refactoring?
- [ ] Is the helper method **well-named** and **single-responsibility**?
- [ ] Are **all write paths** covered (create, update, upsert)?
- [ ] Is existing data **backfilled** if needed?
- [ ] Are tests **integrated** into existing groups (not isolated @Nested classes)?
- [ ] Does the commit message reference the issue? (`(#3723)`)
- [ ] Is the diff **clean**? No unrelated file changes?

## Common Reviewer Questions & How to Address Them

| Reviewer Question | What They Want | How to Address |
|---|---|---|
| "Can we have something more generic?" | Extract a reusable helper method | Create `private void ensureX()` called from all paths |
| "Do we want to verify on DB level?" | Data migration for retrocompatibility | Add Flyway migration to backfill + explain why/why not NOT NULL |
| "Can be better to modify existing tests" | Don't create separate bug-fix test classes | Move assertions into existing `@Nested` groups |
| "What about update?" | You missed a CRUD path | Check all service methods that write the field |
| "Model level approach?" | Logic in entity instead of service | Explain BeanUtils.copyProperties risk if applicable |

## Quick Reference

```bash
# Pre-push verification sequence
mvn spotless:apply -pl openaev-api
mvn spotless:check -pl openaev-api
mvn test -pl openaev-api -Dtest="{TestClass}"
git diff --stat  # verify only expected files changed
git add <specific-files>
git commit -S -m "[backend] fix(scope): description (#ISSUE)"
```
