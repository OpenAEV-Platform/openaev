# Feature Specification: Chaining Swimlanes Integration

**Spec ID**: SPEC-001
**Feature Branch**: `poc/attack-path`
**Created**: 2026-04-27
**Status**: Ready
**Author**: Seb

---

## 1. Summary

Integrate the chaining swimlanes UI (from `seb-chaining-swimlanes` branch) with the backend chaining engine on `release/current`. The swimlanes UI provides a kill-chain-based visual editor for designing attack workflows — users drag & drop inject actions into kill chain phase columns, define conditions/events between them, and execute them as chained scenarios.

The frontend was built ahead of the backend stabilization, creating API contract mismatches that must be resolved. This spec covers: rebasing the swimlanes code, aligning frontend API calls with backend endpoints, adding the `CHAINING_SWIMLANES` feature flag, and ensuring end-to-end flow from scenario creation to execution results.

## 2. User Stories & Acceptance Criteria

### US-1: Create a chaining scenario via swimlanes UI (Priority: P1) 🎯 MVP

**As a** planner with MANAGE_SCENARIO capability, **I want** to create a chaining scenario and design its attack workflow via the swimlanes visual editor, **so that** I can orchestrate inject execution along kill chain phases.

**Why this priority**: Core functionality — without this, swimlanes UI is unusable.

**Acceptance Scenarios**:

```gherkin
Scenario: Planner creates a chaining scenario
  Given I am logged in as a planner with MANAGE_SCENARIO capability
  And INJECT_CHAINING and CHAINING_SWIMLANES feature flags are enabled
  When I create a new scenario and select type "chaining"
  Then a Workflow TEMPLATE is created for the scenario
  And I see the Logic tab with an empty swimlanes canvas

Scenario: Planner adds an action step to the workflow
  Given I have a chaining scenario with an empty workflow
  When I add an action step with injector contract "NetExec SMB"
  Then the step appears in the correct kill chain phase column
  And the step is persisted as a Step TEMPLATE in the backend

Scenario: Planner adds a condition between steps
  Given I have a workflow with two action steps
  When I create a DEPEND_ON condition from step A to step B
  Then an edge appears between the two nodes in the graph
  And step B will only execute after step A completes

Scenario: Feature flag disabled hides swimlanes UI
  Given CHAINING_SWIMLANES feature flag is disabled
  When I navigate to scenarios
  Then I do not see the option to create a chaining scenario
  And the swimlanes Logic tab is not available

Scenario: User without capability is denied
  Given I am logged in as a user without MANAGE_SCENARIO capability
  When I attempt to create a chaining scenario
  Then I receive a 403 Forbidden response
```

---

### US-2: Execute a chaining scenario and see results (Priority: P1) 🎯 MVP

**As a** planner, **I want** to launch a chaining scenario as a simulation and see execution results in the swimlanes view, **so that** I can track which steps succeeded, failed, or are pending.

**Why this priority**: Without execution feedback, the swimlanes is just a design tool with no operational value.

**Acceptance Scenarios**:

```gherkin
Scenario: Launch a chaining simulation
  Given I have a chaining scenario with 3 action steps and conditions
  When I schedule the scenario as a simulation
  Then the workflow engine creates a RUN workflow from the TEMPLATE
  And steps execute according to their conditions

Scenario: View execution status in attack path tab
  Given a chaining simulation is running
  When I open the simulation's Attack Path tab
  Then I see each step with its real-time status (pending/running/detected/prevented/undetected)
  And the view auto-refreshes every 10 seconds

Scenario: Results feed back from inject expectations
  Given step A has completed execution
  When inject expectations are computed
  Then the step output is updated via the WorkflowUpdateEvent AOP
  And dependent steps (step B with DEPEND_ON on A) begin evaluation
```

---

### US-3: Manage workflow scope and configuration (Priority: P2)

**As a** planner, **I want** to configure scope rules (allow/deny lists), rate limiting, and timeouts for my chaining workflow, **so that** execution is controlled and safe.

**Why this priority**: Important for production use but not blocking for MVP integration.

**Acceptance Scenarios**:

```gherkin
Scenario: Configure scope rules
  Given I have a chaining scenario
  When I set allowlist scope rules with specific IP ranges
  Then the rules are persisted as WorkflowScopeRule entities
  And execution respects the scope during simulation

Scenario: Configure rate limiting
  Given I have a chaining scenario
  When I enable rate limiting with max 5 attempts per 60 seconds
  Then the workflow configuration is updated
  And execution pauses between inject batches
```

---

### Edge Cases

- What happens when all steps have unsatisfied conditions? → Workflow ends with status END
- What happens on concurrent condition evaluation? → Queue-based processing ensures serialization
- What if a step's injector contract is deleted after design? → Step execution fails gracefully, step status → END
- What happens when the feature flag is toggled mid-execution? → Running workflows continue, new creations blocked

## 3. Functional Requirements

- **FR-001**: Frontend API calls MUST align with backend endpoint routes and response shapes
- **FR-002**: All swimlanes UI entry points MUST be gated behind `isFeatureEnabled('CHAINING_SWIMLANES')`
- **FR-003**: All swimlanes backend entry points MUST check both `INJECT_CHAINING` AND `CHAINING_SWIMLANES` flags
- **FR-004**: The swimlanes visual editor MUST render steps in kill chain phase columns using ELK layout
- **FR-005**: Workflow mutations (create/update/delete step/condition) MUST return the full updated Workflow object
- **FR-006**: The Attack Path tab MUST show real-time execution status resolved from inject expectations

## 4. Security Requirements

### Access Control

- **Resource Type**: SCENARIO (existing), EXERCISE (existing)
- **Capabilities needed**: ACCESS_SCENARIO, MANAGE_SCENARIO, DELETE_SCENARIO (existing)
- **Access model**: capability-based (existing)
- **`@AccessControl` on every endpoint**: Yes — all chaining endpoints already have it

### Tenant Isolation

- **Tenant-scoped entity**: No — Workflow, Step, Condition implement `Base`, NOT `TenantBase`
- **`@Filter("tenantFilter")`**: Not applied — tenant isolation is enforced via **FK cascade** through parent Scenario/Exercise (which ARE tenant-filtered)
- **Sub-resource pattern**: Step → Workflow → Scenario/Exercise (tenant-filtered)
- **Service-layer requirements**:
  - All Step/Condition queries by ID MUST verify the parent Workflow belongs to a Scenario/Exercise accessible by the caller
  - `ChainingApi.findAll()` MUST be restricted (admin-only or tenant-scoped)
  - List-by-workflow endpoints MUST verify workflow ownership before returning data
- **Native query gap (pre-existing tech debt)**: `StepRepository` contains 5 native queries without tenant scoping. Only called from execution paths where the workflow is already validated. Documented as tech debt for future hardening.
- **`tenant_id` in API response**: Never

### Data Exposure

- **DTO-only responses**: StepApi, ConditionApi, WorkflowApi use DTOs correctly ✅
- **Known violation**: ChainingApi returns raw Exercise/Scenario JPA entities on 4 endpoints (pre-existing). `@JsonIgnore` on tenant relation prevents tenant_id leak, but this is defense-in-depth not defense-by-design. **Remediation**: Create ChainingSimulationOutput/ChainingScenarioOutput DTOs in a follow-up PR.
- **Sensitive fields excluded**: tenant_id, internal passwords not exposed
- **Error messages**: ChainingException messages contain internal IDs (simulation/scenario IDs). **Must use generic messages**: e.g. `"No workflow template found for this simulation"` instead of including the UUID.

### Threat Model

| Threat | Impact | Mitigation |
|--------|--------|------------|
| IDOR via step_id/condition_id | High | @AccessControl + service-layer parent ownership verification |
| Cross-tenant workflow access | Critical | FK cascade to tenant-filtered Scenario/Exercise + service-layer ownership check |
| ChainingApi.findAll() cross-tenant leak | High | Restrict to admin-only or add tenant scoping |
| Inject data injection via step_data JSONB | Low | JSONB cast via PostgreSQL native type, no string interpolation |
| Feature flag bypass (direct API call) | Medium | Add inline flag check on StepApi/ConditionApi (FR-003) |
| Unbounded list responses (DoS) | Medium | Tech debt — add pagination post-MVP |
| Exception message information disclosure | Low | Use generic error messages in ChainingException |

## 5. Technical Context

### Affected Modules

| Module | Changes |
|--------|---------|
| `openaev-model` | No new entities — Workflow, Step, Condition already exist |
| `openaev-api` | Align endpoint routes/responses with frontend contract; add CHAINING_SWIMLANES flag checks |
| `openaev-front` | Rebase swimlanes code; adapt API calls to match backend; add feature flag guards |

### Feature Flag

- **Enum value**: `PreviewFeature.CHAINING_SWIMLANES` (already registered)
- **Backend guard**: `previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_SWIMLANES)` on swimlanes-specific endpoints
- **Frontend guard**: `isFeatureEnabled('CHAINING_SWIMLANES')` on scenario type picker, Logic tab, Attack Path tab
- **Prerequisite**: `INJECT_CHAINING` must also be enabled (base engine)
- **Graduation criteria**: After 1 release cycle with no regressions on chaining tests

### API Contract Alignment

The core integration gap. Frontend and backend must agree on routes and shapes.

### API Contract Alignment — Option A (Adapter le frontend)

**Actual backend routes** (verified against source code):

| Frontend action | Backend call | Response shape |
|---|---|---|
| Load workflow config | `GET /api/workflows/{workflowId}/workflow-configuration` | `WorkflowConfigurationOutput` (rate-limit, timeout, safe-mode, scope rules **only**) |
| Load workflow steps | `GET /api/chaining/steps?workflow_id={workflowId}` | `List<StepOutput>` |
| Load workflow conditions | `GET /api/chaining/conditions?workflow_id={workflowId}` | `List<EventOutput>` |
| Create step | `POST /api/chaining/steps` body: `StepInput` (with `step_workflow_id`) | `StepOutput` |
| Update step | `PUT /api/chaining/steps/{stepId}` body: `StepInput` | `StepOutput` |
| Delete step | `DELETE /api/chaining/steps/{stepId}` | `204` |
| Create condition tree | `POST /api/chaining/conditions` body: `EventInput` (with `event_workflow_id`, `event_conditions[]`) | `EventOutput` |
| Update condition tree | `PUT /api/chaining/conditions/{conditionId}` body: `EventInput` | `EventOutput` |
| Delete condition tree | `DELETE /api/chaining/conditions/{conditionId}` | `204` |

**⚠️ Key gap**: No single endpoint returns the full workflow composite (config + steps + conditions).
Two options:
- **(a) 3 parallel GETs** after each mutation — ✅ **CHOSEN**: simple, no backend change, acceptable overhead for workflow design-time interactions
- ~~(b) New composite endpoint~~ — deferred, may revisit if UX latency is noticeable

**Note**: Condition API uses **Event** terminology — `EventInput`/`EventOutput` wraps a condition tree (root with name/description + child conditions). Not flat conditions.

### Scenario → Workflow Discovery

`ScenarioOutput.scenario_workflow_id` provides the workflowId. There is **no** `isChaining` boolean field on the `Scenario` entity — chaining status is inferred from `scenario_workflow_id != null`. The frontend MUST use this convention instead of a `scenario_type` field.

### Key Entities (existing, no changes)

- **Workflow**: Links to Scenario/Exercise, contains Steps, has scope rules + config
- **Step**: Template/instance pattern, JSONB data containing serialized inject
- **Condition**: Tree structure (AND/OR gates), typed conditions (DEPEND_ON, AFTER, BEFORE, EQ...)
- **WorkflowScopeRule**: Allow/deny lists by IP/subnet/asset

### Scenario Type Field

There is **no** `isChaining` boolean on the `Scenario` entity. `ScenarioInput.isChaining` is accepted but not persisted as a distinct field — the chaining status is implicit: a scenario is "chaining" when it has an associated Workflow (`scenario_workflow_id IS NOT NULL`).

`ScenarioOutput.scenario_workflow_id` returns the workflowId or null. Frontend must check `scenario_workflow_id != null` to determine chaining mode.

## 6. Test Plan

### Backend Tests

- [ ] Integration test: Workflow CRUD via scenario (create scenario → get workflow → add steps → add conditions)
- [ ] Integration test: Feature flag disabled → endpoints return 404
- [ ] Integration test: Execution flow (launch → step ready → step run → update → chain forward)
- [ ] Existing tests: Verify all `WorkflowServiceTest`, `StepServiceTest`, `ConditionServiceTest` still pass after changes

### Frontend Tests

- [ ] Vitest: LogicFlow renders kill chain columns from mock workflow data
- [ ] Vitest: Feature flag disabled hides chaining scenario option
- [ ] E2E (Playwright): Create chaining scenario → add steps → verify persistence

### Security Tests

- [ ] RBAC: Access workflow without MANAGE_SCENARIO → 403
- [ ] Tenant isolation: Access other tenant's workflow → 404
- [ ] Tenant isolation: StepApi GET with another tenant's workflow_id → empty/403
- [ ] Tenant isolation: ChainingApi findAll returns only current tenant's data
- [ ] Feature flag: Direct API call with flag disabled → 404
- [ ] Input validation: ConditionCreateInput with null type → 400, not 500
- [ ] JSONB: StepInput with malformed step_data → 400, not 500

## 7. Success Criteria

- **SC-001**: A planner can create a chaining scenario, add ≥3 steps with conditions via swimlanes UI, and see them persisted in the backend
- **SC-002**: A launched chaining simulation executes steps in order and results are visible in the Attack Path tab
- **SC-003**: All existing chaining tests pass without regression
- **SC-004**: Swimlanes UI is completely invisible when `CHAINING_SWIMLANES` flag is disabled

## 8. Assumptions & Constraints

- Rebase is local only — no force push to any remote branch
- The backend chaining engine (moteur) is functional on `release/current` — no engine changes needed
- Kill chain phases and attack patterns data already exist in the platform (seeded by STIX import)
- The `CHAINING_SWIMLANES` flag is already registered in `PreviewFeature.java`
- `INJECT_CHAINING` flag must be enabled as a prerequisite

## 9. Agent Review Log

### Product Agent Review

- **Date**: 2026-04-27
- **Status**: ✅ Approved
- **Findings**: 4 user stories with full Gherkin coverage. P1 covers MVP (create + execute). P2 covers configuration. Edge cases documented.

### Staff Agent Review

- **Date**: 2026-04-27
- **Status**: ⚠️ Approved with notes (3 blockers resolved in spec update)
- **Findings**:
  1. ~~BLOCKER~~: Workflow route corrected (`/api/workflows/{id}/workflow-configuration`)
  2. ~~BLOCKER~~: No composite endpoint — documented 3-call pattern vs new composite endpoint (decision needed)
  3. ~~BLOCKER~~: `isChaining` doesn't exist on entity — documented inference from `workflowId != null`
  4. Condition API uses Event terminology (EventInput/EventOutput) — documented
  5. Step/Condition list endpoints return unbounded `List<T>` — tech debt, acceptable for MVP
  6. No feature flag check on StepApi/ConditionApi — must be added
  7. ChainingApi returns raw JPA entities (pre-existing violation, not in scope)
  8. Missing `@Fetch(FetchMode.SUBSELECT)` on Workflow collections (pre-existing, not in scope)
  9. Missing `@LogExecutionTime` on all chaining endpoints (pre-existing, not in scope)
- **Decision needed**: 3 parallel GETs vs new composite endpoint?
- **Decision needed**: Feature flag dependency enforcement (inline check vs enum prerequisite)?

### Security Agent Review

- **Date**: 2026-04-27
- **Status**: ⚠️ Approved with notes
- **Findings**:
  1. ⚠️ Workflow/Step/Condition use `Base` not `TenantBase` — tenant isolation relies on FK cascade. `ChainingApi.findAll()` is a cross-tenant risk. Documented sub-resource pattern + service-layer ownership verification. (CVSS 7.1)
  2. ⚠️ ChainingApi returns raw Exercise/Scenario entities on 4 endpoints — pre-existing violation. Remediation plan: create DTOs in follow-up PR. (CVSS 5.4)
  3. 5 native queries in StepRepository lack tenant_id — pre-existing tech debt, documented. (CVSS 5.3)
  4. StepApi/ConditionApi lack feature flag checks — covered by FR-003. (CVSS 3.1)
  5. ChainingException messages leak internal IDs — must use generic messages. (CVSS 2.0)
  6. ✅ @AccessControl present on all 17 endpoints across 4 controllers
  7. ✅ DTOs correctly used in StepApi/ConditionApi/WorkflowApi
  8. ✅ @Valid on all request bodies with strong validation
  9. ✅ JSONB fields use PostgreSQL native casting, no injection risk
