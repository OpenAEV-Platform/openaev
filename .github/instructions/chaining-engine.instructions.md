---
applyTo: |
  openaev-api/src/main/java/io/openaev/api/chaining/**,
  openaev-api/src/main/java/io/openaev/service/chaining/**,
  openaev-api/src/main/java/io/openaev/scheduler/jobs/QueueChainingJob.java,
  openaev-api/src/main/java/io/openaev/scheduler/jobs/WorkflowTimeoutJob.java,
  openaev-api/src/main/java/io/openaev/aop/WorkflowUpdateEvent.java,
  openaev-api/src/main/java/io/openaev/aop/WorkflowUpdateEventAspect.java,
  openaev-api/src/main/java/io/openaev/utils/ConditionUtils.java,
  openaev-api/src/main/java/io/openaev/rest/exception/ChainingException.java,
  openaev-model/src/main/java/io/openaev/database/model/Step.java,
  openaev-model/src/main/java/io/openaev/database/model/Workflow.java,
  openaev-model/src/main/java/io/openaev/database/model/Condition.java,
  openaev-model/src/main/java/io/openaev/database/model/ConditionStep.java,
  openaev-model/src/main/java/io/openaev/database/model/WorkflowState.java,
  openaev-model/src/main/java/io/openaev/database/model/WorkflowStateEntries.java,
  openaev-model/src/main/java/io/openaev/database/model/StepDelayQueue.java,
  openaev-model/src/main/java/io/openaev/database/model/WorkflowScopeRule.java,
  openaev-model/src/main/java/io/openaev/database/model/ScopeVariable.java,
  openaev-model/src/main/java/io/openaev/database/model/InjectDependencyConditions.java,
  openaev-model/src/main/java/io/openaev/database/model/StepStatus.java,
  openaev-model/src/main/java/io/openaev/database/model/StepActionClass.java,
  openaev-model/src/main/java/io/openaev/database/model/WorkflowStatus.java,
  openaev-model/src/main/java/io/openaev/database/model/ConditionType.java,
  openaev-model/src/main/java/io/openaev/database/model/ConditionKeyType.java,
  openaev-model/src/main/java/io/openaev/database/model/MappingType.java,
  openaev-model/src/main/java/io/openaev/database/model/ScopeRuleSelectedMode.java,
  openaev-model/src/main/java/io/openaev/database/model/ContractOutputType.java,
  openaev-model/src/main/java/io/openaev/database/repository/StepRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/WorkflowRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/ConditionRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepositoryCustom.java,
  openaev-model/src/main/java/io/openaev/database/repository/WorkflowStateRepositoryCustomImpl.java,
  openaev-model/src/main/java/io/openaev/database/repository/StepDelayQueueRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/WorkflowScopeRuleRepository.java,
  openaev-model/src/main/java/io/openaev/database/repository/ScopeVariableRepository.java,
  openaev-front/src/actions/chaining/**,
  openaev-front/src/admin/components/chaining/**,
  openaev-front/src/components/common/chaining/**
---

# Chaining Engine — Copilot Instructions

## Overview

The **Chaining Engine** enables automated, conditional execution of steps within a simulation workflow.
It orchestrates inject sequences based on events, conditions, outputs, and time constraints.

The execution flow is driven by a **Step Queue + Delay Queue + Job + Pool** architecture using RabbitMQ.

---

## Architecture & Flow

### High-level execution flow (from design diagram: "STEP QUEUE + JOB +++ + POOL")

```
CREATION PHASE:
  Creation of Simulation ─────────────────────────────┐
    • Has all info required to create an Inject       │
    • Has variable parameters                         │
    • Has execution conditions                        │
  Creation of Step Template ──────────────────────────┤
  Creation of Workflow Template ──────────────────────┘

LAUNCH PHASE:
  Launch Simulation
    └─> Creation of Workflow RUN
          └─> [For each step template]
                └─> CONDITIONS EVENT VALID?
                      │
                      ├─ no  ─────────────────────────────────────────────────────────────────────┐
                      │   • Evaluate Events and steps (?)                                         │
                      │   • Add whitelist in pool as global                                       │
                      │                                                                           ▼
                      │                                                                     (no action)
                      │
                      └─ yes → UPDATE LOCAL POOL
                                 └─> CONDITIONS MAPPER VALID?
                                       │
                                       ├─ no  → END
                                       │
                                       └─ yes → INPUT ALREADY EXECUTED?
                                                  │
                                                  ├─ yes → CHECK HASH INPUT / SAVE INPUT
                                                  │          └─> (links back to STEP STATUS TEMPLATE / FINAL STATUS)
                                                  │
                                                  └─ no  → CREATION NEXT STEP(s) EXECUTION
                                                             └─> QUEUE READY ──[consume]──> JOB FETCHES READY STEPS
                                                                   │                              │
                                                                   │                              ├─> Callback event
                                                                   │                              │
                                                                   ▼                              ▼
                                                             PUSH STEP READY              INJECT CREATION + EXECUTION
                                                             (has all conditions valid)    (cf: direct execution)
                                                                   │                              │
                                                                   ▼                              ▼
                                                             STEP STATUS: READY           API CALL BY EXTERNAL SYS + HUMAN
                                                                                                  │
                                                                                                  ▼
                                                                                          Save output as global:
                                                                                            • Output Parser
                                                                                            • Expectation
                                                                                            • Status
                                                                                            • Findings

EXECUTION FEEDBACK LOOP:
  QUEUE UPDATE ──[consume]──> PUSH STEP RUN
                                    │
                                    ▼
                              UPDATE STEP RUN OUTPUTS
                                    │
                                    ▼
                              UPDATE GLOBAL POOL
                                    │
                                    ▼
                              CHECK & GET STEP TEMPLATE THAT NEEDS THIS OUTPUT TYPE
                                    │
                                    ▼
                              END STEP EXECUTION? ─────────────────────────┐
                                    │                                      │
                                    │ (All external ping output received)  │
                                    │                                      │
                                    ├─ no  → (loop back to queue)          │
                                    │                                      │
                                    └─ yes ─────────────────────────────── ▼
                                                                      TIME CONDITION?
                                                                           │
                                                                           ├─ no  → END
                                                                           │
                                                                           └─ yes → SYSTEM DELAY
                                                                                      └─> PUSH STEP TEMPLATE
                                                                                            └─> (loops back to step execution)

STEP STATUS LIFECYCLE (from diagram):
  STEP STATUS TEMPLATE ──> STEP STATUS READY ──> STEP STATUS READY → RUN ──> STEP STATUS RUN + END
                                                                                     │
                                                                                     ▼
                                                                               FINAL STATUS
```

### Timeout flow

```
WorkflowTimeoutJob (Quartz, @DisallowConcurrentExecution)
  └─> Finds expired RUN workflows (timeout from WorkflowConfiguration)
        └─> WorkflowTimeoutService.forceCompleteWorkflowByTimeout()
              └─> Sets workflow status: END
              └─> Terminates active steps (READY/RUN → END)
              └─> Deletes pending delay queue entries
              └─> Completes active injects (→ SUCCESS)
              └─> Finishes associated simulation
```

### Code mapping to design diagram

| Diagram Element | Code Implementation |
|---|---|
| Creation of Simulation | `ChainingApi.createSimulation()` / `createScenarioChaining()` |
| Creation of Step Template | `StepService.createStepTemplate()` |
| Creation of Workflow Template/RUN | `WorkflowService` (status TEMPLATE → RUN) |
| Conditions Event Valid? | `ConditionUtils` evaluation against event conditions |
| Update Local Pool | `WorkflowStateService.propagateToLocalStates()` |
| Conditions Mapper Valid? | `ConditionUtils` evaluation of MAPPER conditions |
| Input Already Executed? | `StepService` hash check |
| Creation Next Step(s) Execution | `StepService.createReadySteps()` |
| Queue Ready | `QueueChainingService` → `workflows-ready` RabbitMQ queue |
| Job Fetches Ready Steps | `StepEventService.handleReadyStepEvent()` (consumed from queue) |
| Push Step Ready | Step cloned from template with status READY |
| Inject Creation + Execution | `InjectExecutionStep.run()` → `InjectService` |
| API Call by External Sys + Human | External callback → `@WorkflowUpdateEvent` → `WorkflowUpdateEventAspect` |
| Save output as global | `WorkflowStateService.syncState()` (Parser, Expectation, Status, Findings) |
| Queue Update | `QueueChainingService` → `workflows-update` RabbitMQ queue |
| Push Step RUN | `StepEventService.handleExternalUpdateEvent()` → `ActionStep.update()` |
| Update Step Run Outputs | `InjectExecutionStep.update()` |
| Update Global Pool | `WorkflowStateService.syncState()` |
| Check & Get Step Template that needs this output type | `WorkflowStateService.propagateToLocalStates()` |
| End Step Execution? | `InjectExecutionStep.end()` — checks all expected outputs received |
| Time Condition? / System Delay | `StepDelayQueueService.pushStepTemplateIntoStepDelayQueue()` |
| Push Step Template (after delay) | `QueueChainingJob.execute()` polls delay queue → `StepService.enqueueReadySteps()` |
| FINAL STATUS | Terminal step status after RUN + END |

---

## Key Concepts

| Concept | Description |
|---|---|
| **Workflow** | Container linking a Simulation/Scenario to a graph of step templates. Has status: `TEMPLATE` → `RUN` → `STOP`/`END`. |
| **WorkflowConfiguration** | Per-workflow settings: timeout, rate-limit, safe-mode, scope rules. |
| **Step Template** | Blueprint step (status `TEMPLATE`). Defines action type, conditions, expected outputs. |
| **Step (READY/RUN/END)** | Runtime instance cloned from a template during execution. |
| **ActionStep** | Interface for step actions. Currently only `InjectExecutionStep` (creates and executes injects). |
| **Global State (WorkflowState)** | Shared state holding all outputs produced during a workflow run. Stored as JSON entries. |
| **Local State** | Per-step state populated by propagation from global state when conditions match. |
| **Condition** | Tree-structured logical rules (AND/OR root + leaf comparisons). Evaluated against pool values. |
| **ConditionStep** | Join entity linking conditions to steps. |
| **Event** | Frontend term for a condition tree (root condition + children). Backend stores only `Condition` entities. |
| **Queue** | Two RabbitMQ queues: `workflows-ready` (step execution) and `workflows-update` (external updates). |
| **StepDelayQueue** | DB-persisted delay queue for time-based conditions. Polled by `QueueChainingJob` (Quartz). |
| **Scope** | Allowlist/denylist rules (`WorkflowScopeRule`) defining which assets are valid targets. |
| **ScopeVariable** | Named variables used within scope rules. |

---

## Enums Reference

| Enum | Values | Description |
|---|---|---|
| `StepStatus` | `TEMPLATE`, `READY`, `RUN`, `END` | Step lifecycle states |
| `WorkflowStatus` | `TEMPLATE`, `RUN`, `STOP`, `END` | Workflow lifecycle states |
| `StepActionClass` | `INJECT_EXECUTION` | Action implementation class selector |
| `ConditionType` | `AND`, `OR`, `EQ`, `NEQ`, `IS_NULL`, `IS_NOT_NULL`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NIN`, `MAPPER`, `DEPEND_ON` | Condition operators and special types |
| `ConditionKeyType` | `EXECUTION_TIME`, `STEP_TEMPLATE_ID`, `Text`, `Status`, `Number`, `Port`, `Portscan`, `IPv4`, `IPv6`, `Credentials`, `CVE`, `Username`, `Share`, `AdminUsername`, `Group`, `Computer`, `PasswordPolicy`, `Delegation`, `Sid`, `Vulnerability`, `AccountWithPasswordNotRequired`, `AsreproastableAccount`, `KerberoastableAccount`, `Asset` | Type of the condition key (determines comparison semantics) |
| `MappingType` | `DEFAULT`, `LOCAL`, `GLOBAL` | Where condition values are resolved from |
| `ScopeRuleSelectedMode` | `ALLOWLIST`, `DENYLIST` | Whether a scope rule includes or excludes assets |

---

## Package Structure

```
io.openaev.api.chaining/              ← API layer (controllers, mappers, DTOs)
  ├── ChainingApi.java                ← Main REST: create simulation/scenario for chaining, findAll
  ├── StepApi.java                    ← CRUD for step templates (scoped to workflow)
  ├── ConditionApi.java               ← CRUD for condition trees (event payload → conditions)
  ├── WorkflowApi.java                ← Workflow configuration + valid assets
  ├── ActionStep.java                 ← Interface: create/ready/run/update/end step actions
  ├── InjectExecutionStep.java        ← Implementation: creates injects from steps
  ├── StepMapper.java                 ← Step entity → StepOutput DTO
  ├── ConditionMapper.java            ← Condition entity → EventOutput DTO
  ├── WorkflowConfigurationMapper.java ← WorkflowConfiguration → DTO
  ├── ScopeAssetMapper.java           ← Asset → ScopeAssetOutput DTO
  ├── DataOutputStep.java             ← Data class for step output data
  ├── DataInputStep.java              ← Data class for step input data
  └── dto/
        ├── ChainingOutput.java       ← Aggregated response (conditions + steps)
        ├── StepOutput.java / StepInput.java
        ├── StepsCreateInput.java     ← Batch step creation input
        ├── EventOutput.java / EventInput.java  ← Frontend "event" = condition tree
        ├── ConditionOutput.java / ConditionCreateInput.java
        ├── MapperConditionOutput.java
        ├── WorkflowOutput.java
        ├── WorkflowConfigurationOutput.java / WorkflowConfigurationInput.java
        ├── WorkflowScopeRuleOutput.java / WorkflowScopeRuleInput.java
        ├── ScopeAssetOutput.java
        └── ScopeVariableOutput.java / ScopeVariableInput.java

io.openaev.service.chaining/          ← Business logic layer
  ├── WorkflowService.java            ← Workflow CRUD, duplication, configuration, feature gate
  ├── StepService.java                ← Step lifecycle: create/copy/ready/enqueue/update/end
  ├── ConditionService.java           ← Condition tree CRUD, linking conditions to steps
  ├── ConditionFactory.java           ← Factory for special conditions (EXECUTION_TIME, DEPEND_ON)
  ├── WorkflowStateService.java       ← Global/local state sync, propagation to dependent steps
  ├── QueueChainingService.java       ← RabbitMQ queue management (ready + update queues)
  ├── QueueChainingServiceCallbackRegistrar.java ← Registers queue consumers at startup
  ├── StepEventService.java           ← Implements StepEventHandler + ExternalUpdateEventHandler
  ├── StepEventHandler.java           ← Interface: handleReadyStepEvent()
  ├── ExternalUpdateEventHandler.java ← Interface: handleExternalUpdateEvent()
  ├── StepEvent.java                  ← Queue message for ready steps (Queueable)
  ├── ExternalUpdateEvent.java        ← Queue message for external updates (Queueable)
  ├── ScopeService.java               ← Asset scope resolution (allowlist/denylist)
  ├── StepDelayQueueService.java      ← DB-persisted delay queue for time conditions
  └── WorkflowTimeoutService.java     ← Finds expired workflows, force-completes them

io.openaev.aop/
  ├── WorkflowUpdateEvent.java        ← Annotation: marks methods that trigger workflow updates
  └── WorkflowUpdateEventAspect.java  ← AOP aspect: publishes ExternalUpdateEvent after inject changes

io.openaev.scheduler.jobs/
  ├── QueueChainingJob.java           ← Quartz job: polls StepDelayQueue for time-delayed steps
  └── WorkflowTimeoutJob.java         ← Quartz job: expires timed-out workflow runs

io.openaev.utils/
  └── ConditionUtils.java             ← Condition evaluation logic (shared between services)

io.openaev.telemetry.metric_collectors/
  ├── ScopeMetricCollector.java       ← Metrics for scope resolution
  └── ChainingSafetyPolicyMetricCollector.java ← Metrics for safety policy enforcement
```

### Model layer (openaev-model)

```
io.openaev.database.model/
  ├── Workflow.java                   ← JPA entity: links to Simulation/Scenario, has status
  ├── Step.java                       ← JPA entity: step template or runtime instance
  ├── Condition.java                  ← JPA entity: tree node (root OR/AND + leaf comparisons)
  ├── ConditionStep.java              ← JPA join entity: links conditions to steps
  ├── WorkflowState.java             ← JPA entity: global/local state (JSON entries)
  ├── WorkflowStateEntries.java       ← POJO: deserialized state entries
  ├── WorkflowScopeRule.java          ← JPA entity: scope allowlist/denylist rule
  ├── ScopeVariable.java              ← JPA entity: named scope variable
  ├── StepDelayQueue.java             ← JPA entity: pending delayed step execution
  ├── StepStatus.java                 ← Enum
  ├── WorkflowStatus.java             ← Enum
  ├── StepActionClass.java            ← Enum
  ├── ConditionType.java              ← Enum
  ├── ConditionKeyType.java           ← Enum
  ├── MappingType.java                ← Enum
  └── ScopeRuleSelectedMode.java      ← Enum

io.openaev.database.repository/
  ├── WorkflowRepository.java
  ├── StepRepository.java
  ├── ConditionRepository.java
  ├── WorkflowStateRepository.java (+ Custom + CustomImpl)
  ├── WorkflowScopeRuleRepository.java
  ├── ScopeVariableRepository.java
  └── StepDelayQueueRepository.java
```

### Frontend

```
openaev-front/src/actions/chaining/
  ├── chaining-actions.ts             ← API calls: fetchChaining, CRUD steps/conditions
  ├── workflow-actions.ts             ← API calls: workflow configuration, valid assets
  ├── workflow-schema.ts              ← Zod schemas for workflow configuration
  └── workflow-helper.d.ts            ← TypeScript type declarations

openaev-front/src/admin/components/chaining/
  ├── ScopeForm.tsx                   ← Main scope configuration form
  ├── ScopeDefinition.tsx             ← Scope definition wrapper
  ├── ScopeRules.tsx                  ← Allowlist/denylist rule management
  ├── ScopeInventoryBox.tsx           ← Asset selection from inventory
  ├── ScopeTimeOut.tsx                ← Timeout configuration
  ├── ScopeRateLimit.tsx              ← Rate-limit configuration
  ├── ScopeVariables.tsx              ← Scope variable management
  ├── ScopeVariableCreateDialog.tsx   ← Dialog to create new variables
  ├── scope-rules-csv.ts             ← CSV export/import for rules
  └── logic/                          ← Visual workflow editor
        ├── Logic.tsx                 ← Main logic view (React Flow-based)
        ├── LogicV1.tsx               ← Legacy logic view
        ├── types.ts                  ← Frontend type definitions
        ├── logic-flow-helpers.ts     ← React Flow layout helpers
        ├── AddComponentButton.tsx    ← Button to add actions/events
        ├── forms/                    ← CRUD forms for actions and events
        │     ├── CreateActionForm.tsx / UpdateActionForm.tsx
        │     ├── CreateEventForm.tsx / UpdateEventForm.tsx
        │     └── MapperConditionRow.ts
        ├── events/                   ← Event (condition tree) UI components
        │     ├── EventCreationForm.tsx
        │     ├── EventConditionRow.tsx
        │     ├── ConfigureEventDetail.tsx
        │     ├── ConditionGroupBuilder.tsx
        │     ├── LogicalOperatorSelect.tsx
        │     └── event-types.ts
        ├── drawer/                   ← Side drawer for action configuration
        │     ├── AddComponentDrawer.tsx
        │     ├── AddActionList.tsx / AddActionFooter.tsx
        │     ├── ConfigureActionDetail.tsx
        │     └── InjectDataFieldItem.tsx
        └── chaining_flow/           ← React Flow nodes and edges
              ├── LogicFlow.tsx
              ├── ChainingFlowConfiguration.tsx
              ├── nodes/ (ActionNode, EventNode, TacticGroupNode)
              └── edges/ (DeletableEdge)

openaev-front/src/components/common/chaining/
  └── ChainingUtils.tsx               ← Shared chaining utilities
```

---

## Coding Rules

### Architecture

- `ActionStep` is the strategy interface. Currently only `InjectExecutionStep` implements it. To add a new action type, implement `ActionStep` and register in `StepService.factoryAction()`.

### Mappers & DTOs

- Use **static mapper methods** (not MapStruct annotations) — see `ConditionMapper.toOutput()`, `StepMapper.toOutput()`.
- Frontend "Event" = Backend "Condition Tree" (root AND/OR condition + child leaf conditions). The naming mismatch is intentional — always document it in API descriptions.
- All API endpoints return DTOs, never entities.

### Services & State

- Services must be **stateless** — all state lives in `WorkflowState` (global/local) or the database.
- Queue interactions must go through `QueueChainingService` — never publish directly to RabbitMQ.
- `QueueChainingServiceCallbackRegistrar` registers consumers at `@PostConstruct`. The `StepEventService` is the proxied bean to ensure `@Transactional` works on callbacks.
- Condition evaluation logic lives in `ConditionUtils` (shared utility) — services delegate to it.

### Step Lifecycle

- Step status transitions: `TEMPLATE` → `READY` → `RUN` → `END`.
- `TEMPLATE` steps are never executed — they are blueprints cloned into `READY` steps.
- When creating a READY step, clone from template and fill content from input.
- Active step statuses are `READY` and `RUN` (see `StepService.ACTIVE_STEP_STATUS`).
- Before executing a step, always check if the workflow run has ended (`workflowService.isWorkflowEnded()`).

### Workflow Lifecycle

- Workflow status transitions: `TEMPLATE` → `RUN` → `END` (normal) or `STOP` (manual).
- `WorkflowTimeoutService` handles timeout expiration — it force-ends workflows, steps, delay queue entries, and injects.
- Default timeout: 3600 seconds (`WorkflowService.DEFAULT_TIMEOUT_SECONDS`).

### Conditions

- Conditions form a tree: root is `AND`/`OR` type, children are leaf comparisons (`EQ`, `NEQ`, `GT`, etc.).
- `MAPPER` type conditions map values between steps (data flow).
- `DEPEND_ON` type conditions create step dependencies (step B waits for step A).
- `ConditionFactory` creates special conditions: `executionOf()` for execution-time conditions, `dependOn()` for step dependencies.

### Queue & Scheduling

- Two RabbitMQ queues configured in `application.properties`: `workflows-ready` and `workflows-update`.
- `QueueChainingJob` is a Quartz job (`@DisallowConcurrentExecution`) that polls `StepDelayQueue` for time-delayed steps.
- `WorkflowTimeoutJob` is a Quartz job that periodically checks for expired workflow runs.
- Time conditions use `StepDelayQueueService.pushStepTemplateIntoStepDelayQueue()` — never use `Thread.sleep()`.

### External Updates (AOP)

- `@WorkflowUpdateEvent` annotation on inject-related methods triggers the `WorkflowUpdateEventAspect`.
- The aspect extracts inject/expectation IDs via SpEL expressions and publishes `ExternalUpdateEvent` to the update queue.
- This is how inject status changes (from executors or human validation) flow back into the chaining engine.

### Scope

- `ScopeService` resolves valid assets by applying allowlist/denylist rules from `WorkflowScopeRule`.
- Rules can target individual assets or asset groups.
- `ScopeVariable` entities provide named variables for scope rules.

### Testing

- Tests exist at multiple levels: unit tests, integration tests, repository tests.
- Key test classes: `StepServiceTest`, `ConditionServiceTest`, `WorkflowServiceTest`, `ChainingIntegrationTest`, `StepEventServiceTest`, `WorkflowTimeoutServiceTest`, `ScopeServiceTest`, `QueueChainingServiceTest`.
- Fixtures: `WorkflowFixture`, composers: `WorkflowComposer`, `ConditionComposer`.
- Always test timeout guards (workflow ended checks) when adding new execution paths.

---

## Anti-Patterns (Never Do)

- ❌ Publishing directly to RabbitMQ without going through `QueueChainingService`.
- ❌ Using `Thread.sleep()` for time delays — use `StepDelayQueueService`.
- ❌ Skipping the feature flag check in new endpoints.
- ❌ Executing a step without checking `workflowService.isWorkflowEnded()`.
- ❌ Returning JPA entities from controllers — always use DTOs.
- ❌ Calling `TenantContext` directly in services — resolve in API layer.
- ❌ Adding conditions without linking them to a step via `ConditionStep`.
- ❌ Creating a READY step without cloning from a TEMPLATE step.
- ❌ Skipping condition evaluation before step execution.

---

## REST API Paths

| Controller | Base Path | Description |
|---|---|---|
| `ChainingApi` | `/{tenant}/chaining` | Main chaining: findAll, create simulation/scenario, duplicate |
| `StepApi` | `/{tenant}/chaining/steps` | CRUD step templates |
| `ConditionApi` | `/{tenant}/chaining/conditions` | CRUD condition trees |
| `WorkflowApi` | `/{tenant}/workflows` | Workflow configuration, valid assets |

All endpoints use `@AccessControl` with appropriate `Action` and `ResourceType`.

---

## Related Cross-Cutting Files

- `WorkflowUpdateEventAspect.java` — AOP bridge from inject lifecycle to chaining engine
- `PreviewFeatureService.java` — Runtime feature flag service
- `PlatformTriggers.java` / `PlatformJobDefinitions.java` — Quartz job registration
- `ConditionUtils.java` — Shared condition evaluation logic
- `BatchingInjectStatusService.java` — Annotated with `@WorkflowUpdateEvent`