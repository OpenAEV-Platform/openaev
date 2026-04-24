---
name: spec-implement
description: >-
  Implements a feature from its spec plan autonomously. Follows the task breakdown,
  commits after each phase, and consults the user when blocked.
---

# Implement from Spec

## Prerequisites

- Validated spec at `.github/specs/SPEC-{NNN}/spec.md` (status: Ready)
- Implementation plan at `.github/specs/SPEC-{NNN}/plan.md`
- Task breakdown at `.github/specs/SPEC-{NNN}/tasks.md`

## Procedure

### Step 1 — Pre-flight Check

1. **Read all spec documents**:
   ```
   .github/specs/SPEC-{NNN}/spec.md
   .github/specs/SPEC-{NNN}/plan.md
   .github/specs/SPEC-{NNN}/tasks.md
   ```

2. **Verify branch**:
   ```bash
   git branch --show-current
   # Must be on feature/xxx branched from release/current
   ```

3. **Verify services** (for tests later):
   ```bash
   docker ps --format "{{.Names}}" | grep -E "pgsql|minio|elasticsearch|rabbitmq"
   ```

4. **Load the task list** into SQL todos for tracking:
   - Parse tasks.md
   - Insert each task into the `todos` table
   - Set dependencies from the phase ordering

### Step 2 — Execute Phase by Phase

For each phase in the plan:

#### Phase 1: Database & Model
1. Create Flyway migration (follow `add-migration` skill)
2. Create JPA entity (follow `backend.instructions.md` → Entities)
3. Create repository
4. Add ResourceType + Capabilities

**Commit**: `[backend] feat({feature}): add entity and migration`

#### Phase 2: Backend Service & API
5. Create service (follow `backend.instructions.md` → Services)
6. Create DTOs as records (follow `backend.instructions.md` → API DTOs)
7. Create mapper
8. Create controller (follow `backend.instructions.md` → New Controllers)
9. Configure access model if needed

**Commit**: `[backend] feat({feature}): add service and API`

#### Phase 3: Tests
10. Create fixture (follow `add-test` skill → Step 1)
11. Create composer (follow `add-test` skill → Step 2)
12. Create integration test (follow `add-test` skill → Step 3)
13. Add security tests (RBAC, tenant isolation)

**Verify**:
```bash
mvn spotless:apply
mvn test -pl openaev-api -Dtest="{Feature}ApiTest"
mvn jacoco:check
```

**Commit**: `[backend] test({feature}): add integration tests`

#### Phase 4: Frontend (if applicable)
14. Run `yarn generate-types-from-api`
15. Create actions, schema, pages
16. Add CASL permissions
17. Add navigation entry

**Verify**:
```bash
cd openaev-front && yarn lint && yarn check-ts && yarn test
```

**Commit**: `[frontend] feat({feature}): add UI pages`

#### Phase 5: Validation
18. Full backend build: `mvn spotless:apply && mvn clean install`
19. Full frontend: `yarn build && yarn lint && yarn check-ts`
20. All tests: `mvn test && mvn jacoco:check`

### Step 3 — Consultation Gates

**Consult the user if**:
- A task fails and the fix is not obvious
- The implementation deviates from the spec
- A new dependency is needed that wasn't in the plan
- Tests reveal a bug in existing code (not in new code)
- Coverage drops below threshold despite new tests

**Do NOT consult the user for**:
- Standard implementation decisions within the plan
- Fixing formatting issues (run `mvn spotless:apply`)
- Minor naming adjustments
- Adding missing imports

### Step 4 — Update Spec Status

After all phases complete:
1. Update spec status to "Implementing → Complete"
2. Mark all tasks as done in tasks.md (`[x]`)
3. Update the Agent Review Log in spec.md

```bash
git add -A
git commit -m "[backend] feat({feature}): complete implementation of SPEC-{NNN}"
```

The implementation is now ready for `/spec review`.
