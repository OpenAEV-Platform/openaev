# Tasks: Chaining Swimlanes Integration

**Spec**: [SPEC-001](./spec.md)
**Plan**: [plan.md](./plan.md)
**Created**: 2026-04-27

## Format

`[ID] [P?] [Story?] Description — file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps to user story (US-1, US-2, US-3)

---

## Phase 0: Rebase (Blocking — highest risk)

> All other phases depend on this. Local only, no force push.

- [ ] T001 [US-1] Create working branch from `origin/seb-chaining-swimlanes` for rebase — git
- [ ] T002 [US-1] Interactive rebase onto `release/current`, resolve conflicts — git
  - Keep chaining files from swimlanes branch
  - Keep all other files from release/current
  - Special attention to restructured paths (deleted `logic/flow/` directory)
- [ ] T003 [US-1] Verify frontend compiles: `yarn install && yarn build` — `openaev-front/`
- [ ] T004 [US-1] Cherry-pick rebased swimlanes commits into `poc/attack-path` — git

**Checkpoint**: Swimlanes code exists on `poc/attack-path` branch, `yarn build` passes

---

## Phase 1: Backend Hardening

> Can start after Phase 0. Independent of Phase 2.

- [ ] T005 [P] [US-1] Add `INJECT_CHAINING` + `CHAINING_SWIMLANES` flag check on StepApi — `openaev-api/src/main/java/io/openaev/api/chaining/StepApi.java`
- [ ] T006 [P] [US-1] Add `INJECT_CHAINING` + `CHAINING_SWIMLANES` flag check on ConditionApi — `openaev-api/src/main/java/io/openaev/api/chaining/ConditionApi.java`
- [ ] T007 [US-2] Replace UUID-leaking ChainingException messages with generic messages — `openaev-api/src/main/java/io/openaev/service/chaining/*.java`
- [ ] T008 Run `mvn spotless:apply` — project root

**Checkpoint**: `mvn spotless:check` passes, flag checks in place

---

## Phase 2: Frontend Alignment

> Can start after Phase 0. Independent of Phase 1.

- [ ] T009 [US-1] Rewrite API calls in `workflow-actions.ts` to match actual backend routes — `openaev-front/src/actions/chaining/workflow-actions.ts`
  - `fetchWorkflowConfig` → `GET /api/workflows/{id}/workflow-configuration`
  - `fetchWorkflowSteps` → `GET /api/chaining/steps?workflow_id={id}`
  - `fetchWorkflowConditions` → `GET /api/chaining/conditions?workflow_id={id}`
  - Create/Update/Delete step → `POST/PUT/DELETE /api/chaining/steps`
  - Create/Update/Delete condition → `POST/PUT/DELETE /api/chaining/conditions`
- [ ] T010 [US-1] Implement 3 parallel GETs pattern for full workflow loading — `openaev-front/src/actions/chaining/workflow-actions.ts`
  - `fetchFullWorkflow(workflowId)` → `Promise.all([config, steps, conditions])`
  - Return composite object matching what components expect
- [ ] T011 [US-1] Update `workflow-helper.d.ts` response types to match backend DTOs — `openaev-front/src/actions/chaining/workflow-helper.d.ts`
  - `StepOutput`, `EventOutput`, `WorkflowConfigurationOutput`
  - Use `api-types.d.ts` generated types where available
- [ ] T012 [US-1] Adapt Event terminology in condition components — `openaev-front/src/admin/components/scenarios/scenario/logic/LogicEventForm.tsx`
  - `EventInput` wraps condition tree (root + children), NOT flat conditions
  - `event_workflow_id` instead of `condition_workflow_id`
- [ ] T013 [US-1] Replace `scenario_type === 'chaining'` with `scenario_workflow_id != null` — multiple frontend files
  - `ScenarioFormChaining.tsx`
  - `ScenarioLogic.tsx`
  - Any component checking chaining status
- [ ] T014 [P] [US-1] Add `isFeatureEnabled('CHAINING_SWIMLANES')` guard on Logic tab — `openaev-front/src/admin/components/scenarios/scenario/logic/ScenarioLogic.tsx`
- [ ] T015 [P] [US-1] Add `isFeatureEnabled('CHAINING_SWIMLANES')` guard on chaining scenario form — `openaev-front/src/admin/components/scenarios/ScenarioFormChaining.tsx`
- [ ] T016 [P] [US-2] Add `isFeatureEnabled('CHAINING_ATTACK_PATH')` guard on Attack Path tab — `openaev-front/src/admin/components/simulations/simulation/attack_path/SimulationAttackPath.tsx`
- [ ] T017 [US-1] Verify `@xyflow/react` and `elkjs` in `package.json` — `openaev-front/package.json`
- [ ] T018 [US-3] Review scope definition components align with WorkflowConfigurationInput — `openaev-front/src/admin/components/simulations/simulation/chaining/ScopeDefinition.tsx`

**Checkpoint**: `yarn build && yarn lint && yarn check-ts` passes

---

## Phase 3: Tests

> Backend tests depend on Phase 1. Frontend tests depend on Phase 2.

### Backend Tests

- [ ] T019 [US-1] Integration test: StepApi with CHAINING_SWIMLANES flag disabled → 404 — `openaev-api/src/test/java/io/openaev/api/chaining/`
- [ ] T020 [US-1] Integration test: ConditionApi with CHAINING_SWIMLANES flag disabled → 404 — `openaev-api/src/test/java/io/openaev/api/chaining/`
- [ ] T021 [US-1] Security test: Cross-tenant workflow access → denied — `openaev-api/src/test/java/io/openaev/api/chaining/`
- [ ] T022 Verify existing tests pass: `mvn test -pl openaev-api -Dtest="*Chaining*,*Workflow*,*Step*"` — project root

### Frontend Tests

- [ ] T023 [P] [US-1] Vitest: Feature flag disabled hides chaining scenario option — `openaev-front/src/__tests__/`
- [ ] T024 [P] [US-1] Vitest: LogicFlow renders kill chain columns from mock data — `openaev-front/src/__tests__/`

**Checkpoint**: `mvn test` passes, `yarn test` passes

---

## Phase 4: Validation & Polish

> Depends on all previous phases.

- [ ] T025 Run full backend build: `mvn spotless:apply && mvn clean install -DskipTests -Pdev`
- [ ] T026 Run full frontend build: `yarn build && yarn lint && yarn check-ts`
- [ ] T027 Run full backend tests: `mvn test`
- [ ] T028 Run full frontend tests: `yarn test`
- [ ] T029 Commit all changes with spec reference
- [ ] T030 Run spec-review pipeline (Product → Staff → Security Reviewer agents)

**Checkpoint**: All tests pass, spec-review agents approve

---

## Dependencies & Execution Order

```
Phase 0 (Rebase) ─┬─→ Phase 1 (Backend) ──→ Phase 3 Backend Tests ─┐
                   └─→ Phase 2 (Frontend) ──→ Phase 3 Frontend Tests ├→ Phase 4 (Validation)
                                                                      │
                                                                      └→ Spec Review
```

### Parallel Opportunities

- T005 + T006 within Phase 1 (different files)
- T014 + T015 + T016 within Phase 2 (different files, same pattern)
- T023 + T024 within Phase 3 (independent test files)
- Phase 1 and Phase 2 after Phase 0 (backend and frontend are independent)

---

## Notes

- Mark tasks `[x]` as completed
- Commit after each phase checkpoint
- Phase 0 is the riskiest — if >2h on conflicts, pivot to cherry-pick strategy
- Pre-existing tech debt (unbounded lists, raw entities on ChainingApi, missing SUBSELECT) is OUT OF SCOPE
- All feature flag guards use inline check: `isEnabled(INJECT_CHAINING) && isEnabled(CHAINING_SWIMLANES)`
