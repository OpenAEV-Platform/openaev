# Implementation Plan: Chaining Swimlanes Integration

**Spec**: [SPEC-001](./spec.md)
**Branch**: `poc/attack-path`
**Created**: 2026-04-27
**Status**: Ready

---

## 1. Summary

Integrate the chaining swimlanes UI (from `seb-chaining-swimlanes` branch) with the existing backend chaining engine on `release/current`. This is an **integration spec** — no new entities, no new migrations. The work is:

1. **Rebase** the swimlanes branch onto `release/current` (resolving ~90 conflicts)
2. **Adapt frontend API calls** to match actual backend routes (3 parallel GETs, Event terminology)
3. **Add feature flag guards** (backend + frontend) for `CHAINING_SWIMLANES`
4. **Harden security**: generic error messages, flag checks on StepApi/ConditionApi
5. **Add tests**: feature flag, security (RBAC, tenant isolation), integration

## 2. Technical Context

**Stack**: Java 21 / Spring Boot / PostgreSQL / Elasticsearch / React / TypeScript
**Modules**: openaev-model, openaev-api, openaev-front
**Testing**: JUnit 5 + MockMvc (backend), Vitest + Playwright (frontend)
**Migrations**: None required — all entities exist (Workflow, Step, Condition, StepState, etc.)
**Next migration slot**: V4_99 (reserved, not expected to be needed)

## 3. Constitution Check

> GATE: Must pass before implementation begins.

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security-First | ✅ | @AccessControl on all 17 endpoints. Tenant isolation via FK cascade documented. FR-003 adds flag checks. |
| II. Layered Architecture | ✅ | No layer violations planned. StepApi/ConditionApi/WorkflowApi use DTOs correctly. ChainingApi raw entities are pre-existing tech debt (follow-up PR). |
| III. Test-First Quality | ✅ | 9 existing test classes + 3 fixtures. Spec §6 defines 4 backend, 3 frontend, 7 security tests. |
| IV. Performance by Design | ⚠️ | Pre-existing: unbounded `List<T>` on step/condition endpoints, missing `@Fetch(FetchMode.SUBSELECT)` on Workflow collections. NOT in scope — documented as tech debt. |
| V. Spec-Driven Development | ✅ | SPEC-001 validated by Product, Staff, and Security agents. |
| VI. Conventional Commits | ✅ | Branch from `release/current`. Commit format: `[context] type(scope): description`. |
| VII. Frontend Discipline | ✅ | Swimlanes use `@xyflow/react`, no MUI layout violations. Direct API calls (not Redux) for workflow data. CASL checks needed. |
| VIII. Feature Flags | ✅ | `CHAINING_SWIMLANES` + `CHAINING_ATTACK_PATH` already registered in `PreviewFeature.java`. |
| IX. Simplicity | ✅ | Option A chosen (adapt frontend) avoids new backend endpoints. 3 parallel GETs avoids composite endpoint complexity. |

## 4. Architecture

### Module Mapping (existing code — modifications only)

```
openaev-api/src/main/java/io/openaev/api/chaining/
├── ChainingApi.java                   # NO CHANGE (raw entities = follow-up PR)
├── StepApi.java                       # ADD: feature flag check
├── ConditionApi.java                  # ADD: feature flag check
├── WorkflowApi.java                   # NO CHANGE (flag on parent scenario suffices)
├── StepMapper.java                    # NO CHANGE
├── ConditionMapper.java               # NO CHANGE
├── WorkflowConfigurationMapper.java   # NO CHANGE
└── dto/                               # NO CHANGE (all DTOs exist)

openaev-api/src/main/java/io/openaev/service/chaining/
├── WorkflowService.java               # POSSIBLE: generic error messages
├── StepService.java                    # POSSIBLE: generic error messages
└── ConditionService.java              # POSSIBLE: generic error messages

openaev-api/src/test/java/io/openaev/
├── api/chaining/
│   └── StepApiFeatureFlagTest.java    # NEW: feature flag + security tests
├── utils/fixtures/
│   ├── WorkflowFixture.java           # EXISTS
│   ├── StepFixture.java               # EXISTS
│   └── ConditionFixture.java          # EXISTS

openaev-front/src/
├── actions/chaining/
│   ├── workflow-actions.ts            # REWRITE: align with actual backend routes
│   ├── workflow-helper.d.ts           # UPDATE: align response types
│   └── workflow-schema.ts             # REVIEW: Zod schema alignment
├── admin/components/scenarios/scenario/logic/
│   ├── ScenarioLogic.tsx              # UPDATE: flag guard + 3 parallel GETs
│   ├── LogicFlow.tsx                  # UPDATE: API response adaptation
│   └── ... (14 files)                 # REBASE: resolve conflicts
├── admin/components/simulations/simulation/
│   ├── attack_path/                   # UPDATE: flag guard (CHAINING_ATTACK_PATH)
│   ├── chaining/                      # REBASE: scope definition UI
│   └── SimulationAttackPath.tsx       # UPDATE: flag guard
└── admin/components/scenarios/
    └── ScenarioFormChaining.tsx        # UPDATE: use workflowId != null check
```

### API Contract (existing — no changes)

| Operation | Endpoint | Response |
|-----------|----------|----------|
| Load config | `GET /api/workflows/{workflowId}/workflow-configuration` | `WorkflowConfigurationOutput` |
| Load steps | `GET /api/chaining/steps?workflow_id={id}` | `List<StepOutput>` |
| Load conditions | `GET /api/chaining/conditions?workflow_id={id}` | `List<EventOutput>` |
| Create step | `POST /api/chaining/steps` | `StepOutput` |
| Update step | `PUT /api/chaining/steps/{stepId}` | `StepOutput` |
| Delete step | `DELETE /api/chaining/steps/{stepId}` | `204` |
| Create condition | `POST /api/chaining/conditions` | `EventOutput` |
| Update condition | `PUT /api/chaining/conditions/{conditionId}` | `EventOutput` |
| Delete condition | `DELETE /api/chaining/conditions/{conditionId}` | `204` |

## 5. Implementation Phases

### Phase 0: Rebase (Blocking — highest risk)

Rebase `seb-chaining-swimlanes` onto `release/current`. This is the single riskiest step.

**Strategy**:
1. Create a working branch from the swimlanes branch
2. Interactive rebase onto `release/current`
3. For each conflict batch, resolve keeping swimlanes changes for chaining files and release/current for everything else
4. Verify build compiles after rebase

**Rollback**: If rebase becomes unmanageable (>2h manual conflict resolution), pivot to cherry-pick of the 8 frontend-only commits with manual file placement.

### Phase 1: Backend Hardening

1. Add feature flag check (`INJECT_CHAINING` + `CHAINING_SWIMLANES`) on StepApi and ConditionApi
2. Replace ChainingException messages that leak UUIDs with generic messages
3. Run `mvn spotless:apply`

### Phase 2: Frontend Alignment

4. Rewrite `workflow-actions.ts` API calls to match actual backend routes
5. Replace single workflow fetch with 3 parallel GETs (config + steps + conditions)
6. Adapt Event terminology (EventInput/EventOutput for conditions)
7. Replace `scenario_type === 'chaining'` checks with `scenario_workflow_id != null`
8. Add `isFeatureEnabled('CHAINING_SWIMLANES')` guards on Logic tab, scenario form
9. Add `isFeatureEnabled('CHAINING_ATTACK_PATH')` guards on Attack Path tab
10. Verify `@xyflow/react` and `elkjs` are in package.json dependencies

### Phase 3: Tests

11. Add feature flag integration tests (flag disabled → 404 on StepApi/ConditionApi)
12. Add tenant isolation test (cross-tenant workflow access → denied)
13. Verify all 9 existing chaining test classes pass
14. Frontend: Vitest test for feature flag gating

### Phase 4: Validation

15. `mvn spotless:apply && mvn clean install -DskipTests -Pdev`
16. `mvn test` (full backend test suite)
17. `cd openaev-front && yarn install && yarn build && yarn lint && yarn check-ts`
18. `yarn test` (Vitest)
19. Manual smoke test: enable flags, create chaining scenario, add steps, execute

## 6. Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Rebase conflicts unresolvable | Critical | Medium | Pivot to cherry-pick + manual file placement |
| Swimlanes depends on deleted `logic/flow/` path | High | High | Files were restructured on release/current; must recreate directory or update imports |
| `@xyflow/react` / `elkjs` version incompatible | Medium | Low | Lock versions from swimlanes branch package.json |
| Backend response shapes don't match frontend expectations | High | Medium | 3 parallel GETs pattern documented; adapter layer in workflow-actions.ts |
| Attack Path tab depends on inject expectations not yet wired | Medium | Medium | Show mock/pending states; real data requires active simulation |

## 7. Dependencies

- **Internal**: `INJECT_CHAINING` feature flag must exist (✅ already registered)
- **Internal**: `CHAINING_SWIMLANES` feature flag must exist (✅ already registered)
- **Internal**: Kill chain phases + attack patterns data seeded (✅ STIX import)
- **External**: `@xyflow/react` + `elkjs` npm packages
- **Ordering**: Phase 0 (rebase) → Phase 1 (backend) → Phase 2 (frontend) → Phase 3 (tests) → Phase 4 (validation)
- **Parallel**: Phase 1 and Phase 2 can run in parallel after Phase 0
- **Parallel**: Phase 3 backend tests can start after Phase 1; frontend tests after Phase 2
