---
name: spec-review
description: >-
  Post-implementation review pipeline: product → staff → security agents verify the implementation
  matches the spec. Blockers are fixed directly. Use after implementation is complete.
---

# Post-Implementation Review

## Prerequisites

- Completed implementation (spec status: "Complete")
- Spec at `.github/specs/SPEC-{NNN}/spec.md`
- All code committed on the feature branch

## Procedure

### Step 1 — Gather Changed Files

```bash
# List all files changed in this feature branch
git --no-pager diff --name-only origin/release/current...HEAD
```

### Step 2 — Product Review 📋

Launch the **Product Reviewer** agent in post-implementation mode:

**Agent task**: "Review the implementation against the spec at `.github/specs/SPEC-{NNN}/spec.md`. Verify all acceptance criteria are covered by tests and working code. Check the changed files: {file list}"

The Product Agent checks:
1. Every P1 acceptance scenario has a passing test
2. Every P2+ scenario is implemented or explicitly deferred
3. Edge cases from the spec are handled
4. Error messages are user-friendly
5. Success criteria can be measured

**If blocker found**:
- Fix the issue directly (add missing test, fix behavior)
- Re-run the affected tests
- Commit: `[backend] fix({feature}): address product review finding`

**If no blocker**: Update Agent Review Log → Product: ✅ Approved

### Step 3 — Staff Review 🏗️

Launch the **Staff Reviewer** agent in post-implementation mode:

**Agent task**: "Review the code quality of the implementation for SPEC-{NNN}. Check layering, anti-patterns, naming conventions, DTO usage, transaction annotations. Changed files: {file list}"

The Staff Agent checks (using `review-staff/SKILL.md`):
1. Layering: Controller → Service → Repository
2. DTOs: No JPA entities in API responses
3. Anti-patterns: No spaghetti, god class, duplication
4. Naming: Column naming, @JsonProperty, section comments
5. Transactions: Correct import, readOnly on reads
6. Frontend: No MUI layout, sx only, auto-generated types

**If blocker found**:
- Fix the issue directly (refactor, rename, add DTOs)
- Re-run `mvn spotless:apply && mvn test`
- Commit: `[backend] refactor({feature}): address staff review finding`

**If no blocker**: Update Agent Review Log → Staff: ✅ Approved

### Step 4 — Security Review 🔒

Launch the **Security Reviewer** agent in code review mode:

**Agent task**: "Security review the implementation for SPEC-{NNN}. Check @AccessControl, tenant isolation, data exposure, secrets, input validation. Changed files: {file list}"

The Security Agent checks (using `review-security/SKILL.md`):
1. @AccessControl on every endpoint
2. Tenant isolation: @Filter, native query guards
3. Data exposure: No tenant_id in responses, no stack traces
4. Secrets: No hardcoded credentials
5. Input validation: @NotBlank, @Valid on request bodies

**If blocker found**:
- Fix the issue directly (add @AccessControl, add tenant filter)
- Re-run tests
- Commit: `[backend] fix({feature}): address security review finding`

**If no blocker**: Update Agent Review Log → Security: ✅ Approved

### Step 5 — Final Status

After all three reviews pass:

1. Update spec status to "Reviewed"
2. Update Agent Review Log with dates and statuses
3. Commit review log updates

```bash
git add .github/specs/SPEC-{NNN}/spec.md
git commit -m "[agent] chore(spec): complete review for SPEC-{NNN}"
```

The implementation is now ready for `/spec test`.
