# Tasks: [FEATURE NAME]

**Spec**: [SPEC-NNN](../specs/SPEC-NNN-feature-name/spec.md)
**Plan**: [plan.md](./plan.md)
**Created**: [DATE]

## Format

`[ID] [P?] [Story?] Description — file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps to user story (US-1, US-2, etc.)

---

## Phase 1: Database & Model (Blocking)

> No other phase can start until this is complete.

- [ ] T001 Create migration `V4_{XX}__{Description}.java` — `openaev-api/src/main/java/io/openaev/migration/`
- [ ] T002 Create JPA entity `{Entity}.java` — `openaev-model/src/main/java/io/openaev/database/model/`
- [ ] T003 [P] Create repository `{Entity}Repository.java` — `openaev-model/src/main/java/io/openaev/database/repository/`
- [ ] T004 [P] Add `ResourceType.{ENTITY}` + capabilities — `openaev-model/src/main/java/io/openaev/database/model/`

**Checkpoint**: Entity created, migration runs, schema valid

---

## Phase 2: Backend Service & API

> Depends on Phase 1 completion.

- [ ] T005 Create service `{Feature}Service.java` — `openaev-api/src/main/java/io/openaev/service/`
- [ ] T006 [P] Create DTO `{Entity}Input.java` — `openaev-api/src/main/java/io/openaev/api/{feature}/`
- [ ] T007 [P] Create DTO `{Entity}Output.java` — `openaev-api/src/main/java/io/openaev/api/{feature}/`
- [ ] T008 Create mapper `{Entity}Mapper.java` — `openaev-api/src/main/java/io/openaev/api/{feature}/`
- [ ] T009 Create controller `{Feature}Api.java` — `openaev-api/src/main/java/io/openaev/api/{feature}/`
- [ ] T010 [P] Configure access model in `PermissionService.java` (if needed)

**Checkpoint**: API endpoints accessible, `mvn spotless:apply` passes

---

## Phase 3: Tests

> Depends on Phase 2 completion.

- [ ] T011 [P] Create fixture `{Entity}Fixture.java` — `openaev-api/src/test/java/io/openaev/utils/fixtures/files/`
- [ ] T012 [P] Create composer `{Entity}Composer.java` — `openaev-api/src/test/java/io/openaev/utils/fixtures/composers/`
- [ ] T013 Create integration test `{Feature}ApiTest.java` — `openaev-api/src/test/java/io/openaev/rest/`
  - [ ] T013a [US-1] CRUD operations (create, read, update, delete, search)
  - [ ] T013b [US-1] Permission checks (with/without capabilities)
  - [ ] T013c [US-1] Tenant isolation (cross-tenant access denied)
  - [ ] T013d [US-2+] Additional user story scenarios
- [ ] T014 [P] Unit tests (if complex business logic exists)

**Checkpoint**: `mvn test -Dtest="{Feature}ApiTest"` passes, `mvn jacoco:check` passes

---

## Phase 4: Frontend (if applicable)

> Can start after Phase 2 checkpoint (API available). Independent of Phase 3.

- [ ] T015 Run `yarn generate-types-from-api` to get updated types
- [ ] T016 [P] Create actions `{feature}-action.ts` — `openaev-front/src/actions/{feature}/`
- [ ] T017 [P] Create Zod schema `{feature}-schema.ts` — `openaev-front/src/actions/{feature}/`
- [ ] T018 Create list page `{Feature}s.tsx` — `openaev-front/src/admin/components/{section}/{feature}/`
- [ ] T019 Create form `{Feature}Form.tsx` — `openaev-front/src/admin/components/{section}/{feature}/`
- [ ] T020 [P] Add CASL subject in `types.ts` — `openaev-front/src/utils/permissions/`
- [ ] T021 [P] Add navigation entry (if top-level feature)

**Checkpoint**: `yarn lint && yarn check-ts && yarn test` passes

---

## Phase 5: Validation & Polish

> Depends on all previous phases.

- [ ] T022 Run full backend build: `mvn spotless:apply && mvn clean install`
- [ ] T023 Run full frontend build: `yarn build && yarn lint && yarn check-ts`
- [ ] T024 Run backend tests: `mvn test && mvn jacoco:check`
- [ ] T025 Run frontend tests: `yarn test`
- [ ] T026 [P] E2E tests: `yarn test:e2e` (if E2E scenarios defined in spec)
- [ ] T027 [P] Security scan (if security tests defined in spec)

**Checkpoint**: All tests pass, coverage meets thresholds, no security findings

---

## Dependencies & Execution Order

```
Phase 1 (Database) ──→ Phase 2 (Backend) ──→ Phase 3 (Tests)
                                          ──→ Phase 4 (Frontend)
                                                      ↓
                                               Phase 5 (Validation)
```

### Parallel Opportunities

- T003 + T004 within Phase 1
- T006 + T007 within Phase 2
- T011 + T012 within Phase 3
- T016 + T017 + T020 + T021 within Phase 4
- Phase 3 and Phase 4 can run in parallel after Phase 2

---

## Notes

- Mark tasks `[x]` as completed
- Commit after each phase checkpoint
- Stop at any checkpoint to run agent reviews if needed
- If a task reveals a spec gap, update the spec before continuing
