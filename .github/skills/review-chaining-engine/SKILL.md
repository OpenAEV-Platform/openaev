# Skill: Review Chaining Engine

## Purpose

This skill guides Copilot when reviewing or modifying the **Chaining Engine** of OpenAEV.
It covers the full feature: step lifecycle, condition evaluation, queue processing, state management,
timeout handling, scope resolution, and the AOP bridge to inject lifecycle.

---

## When to use this skill

- Reviewing a PR that touches chaining code (API, service, model, scheduler)
- Adding a new **Step action type** (implementing `ActionStep`)
- Adding a new **Condition type** (extending `ConditionType` enum)
- Modifying the **Global/Local State** synchronization logic
- Adding or modifying **queue processing** (ready queue, update queue, delay queue)
- Modifying **timeout** or **scope** logic
- Adding new endpoints that need `@WorkflowUpdateEvent` annotation
- Debugging step execution flow or queue processing

---

## Review Checklist

### 1. Step Lifecycle Integrity
- [ ] Status transitions follow: `TEMPLATE` → `READY` → `RUN` → `END`
- [ ] TEMPLATE steps are never executed directly — always cloned to READY
- [ ] Before execution, `workflowService.isWorkflowEnded()` is checked
- [ ] Active steps are identified by `StepStatus.READY` or `StepStatus.RUN`

### 2. Workflow Lifecycle
- [ ] Workflow status transitions: `TEMPLATE` → `RUN` → `END`/`STOP`
- [ ] Timeout handling doesn't leave orphaned READY/RUN steps
- [ ] Duplication correctly copies templates and conditions

### 3. Queue & Scheduling
- [ ] All queue publishing goes through `QueueChainingService`
- [ ] No direct RabbitMQ calls from services
- [ ] Time delays use `StepDelayQueueService`, never `Thread.sleep()`
- [ ] Quartz jobs handle concurrent execution correctly (`@DisallowConcurrentExecution`)

### 4. State Management
- [ ] Global state updated via `WorkflowStateService.syncState()` BEFORE propagation
- [ ] Local state propagation uses `propagateToLocalStates()`
- [ ] State entries are properly serialized/deserialized (Gson)

### 5. Conditions
- [ ] Conditions evaluated BEFORE step execution
- [ ] Tree structure maintained: root (AND/OR) + leaf conditions
- [ ] `DEPEND_ON` conditions use `ConditionFactory.dependOn()`
- [ ] `MAPPER` conditions properly resolve from LOCAL or GLOBAL pool
- [ ] Conditions linked to steps via `ConditionStep` entity

### 6. External Update Bridge (AOP)
- [ ] Methods that mutate inject status are annotated with `@WorkflowUpdateEvent`
- [ ] SpEL expressions correctly extract inject/expectation IDs
- [ ] Exactly one of `injectId`, `injectIds`, or `expectationIds` is specified per annotation

### 7. Scope
- [ ] Allowlist rules are applied first, then denylist exclusions
- [ ] Both individual assets and asset groups are handled
- [ ] IP address matching uses `IpAddressUtils`

### 8. API Layer
- [ ] All endpoints have `@AccessControl` with correct `Action` and `ResourceType`
- [ ] DTOs are returned (never entities)
- [ ] Static mapper methods used (not MapStruct annotation processing)
- [ ] `@Transactional(rollbackFor = Exception.class)` on write operations
- [ ] Proper HTTP status codes (201 for creation, 204 for deletion)

### 9. Error Handling
- [ ] `ChainingException` used for chaining-specific errors
- [ ] No swallowed exceptions in queue consumers (at minimum logged)

---

## Step-by-step: Add a new Step action type

1. **Create the class** — implement `ActionStep` interface in `io.openaev.api.chaining`.
2. **Implement all methods** — `create()`, `ready()`, `run()`, `update()`, `end()`.
3. **Register in factory** — add to `StepService.factoryAction()` switch/map.
4. **Add enum value** — extend `StepActionClass` with the new action class name.
5. **Add tests** — unit test each lifecycle method; integration test the full flow.

---

## Step-by-step: Add a new Condition type

1. **Add enum value** — extend `ConditionType` with the new type.
2. **Update evaluation** — add logic in `ConditionUtils` for the new type.
3. **Update factory (if special)** — add a factory method in `ConditionFactory`.
4. **Update mapper** — extend `ConditionMapper` if the new type has special serialization.
5. **Write tests** — test condition evaluation in isolation and in a workflow context.

---

## Step-by-step: Add a new API endpoint

1. Add the method in the relevant `*Api.java` file.
2. Add `@AccessControl` with appropriate `Action` and `ResourceType`.
3. Add `@Transactional(rollbackFor = Exception.class)` for writes.
4. Create input/output DTOs in `io.openaev.api.chaining.dto`.
5. Add static mapper methods (not MapStruct annotations).
6. Implement service logic in `io.openaev.service.chaining`.
7. Write integration test following existing patterns (`StepApiTest`, `ConditionApiTest`, `WorkflowApiTest`).

---

## Key Invariants (Never Break)

- ✅ Endpoint access is enforced via `@AccessControl` (including EE validation where required)
- ✅ Conditions evaluated before step execution
- ✅ Global state updated before local state propagation
- ✅ Step status follows TEMPLATE → READY → RUN → END
- ✅ Workflow ended check before any step execution
- ✅ Queue interactions go only through `QueueChainingService`
- ✅ Time delays use `StepDelayQueueService`
- ✅ Static mapper methods for entity ↔ DTO conversions
- ✅ `@WorkflowUpdateEvent` on inject-mutating methods
- ✅ Timeout force-completes all active steps and clears delay queue

---

## Test Fixtures & Composers

| Class               | Purpose                                |
|---------------------|----------------------------------------|
| `WorkflowFixture`   | Creates test workflow entities         |
| `WorkflowComposer`  | Builds complex workflow test scenarios |
| `ConditionComposer` | Builds condition trees for tests       |
| `StepComposer`      | Builds Step tests            |

## Key Test Classes

| Test | Scope |
|---|---|
| `ChainingIntegrationTest` | End-to-end chaining flow |
| `StepServiceTest` / `StepServiceIntegrationTest` | Step lifecycle |
| `StepServiceScenarioIntegrationTest` | Scenario-based chaining |
| `ConditionServiceTest` | Condition CRUD and evaluation |
| `WorkflowServiceTest` | Workflow CRUD and configuration |
| `WorkflowStateServiceTest` | State sync and propagation |
| `StepEventServiceTest` | Queue event handling |
| `QueueChainingServiceTest` | Queue management |
| `ScopeServiceTest` | Scope resolution |
| `WorkflowEndServiceTest` / `WorkflowTimeoutIntegrationTest` | Timeout handling |
| `StepDelayQueueServiceTest` / `StepDelayQueueIntegrationTest` | Delay queue |
| `StepApiTest` / `ConditionApiTest` / `WorkflowApiTest` | API layer |

---

## Useful Links

- [Chaining PRs](https://github.com/OpenAEV-Platform/openaev/pulls?q=is%3Apr+chaining+draft%3Afalse)
- Instructions: [chaining-engine.instructions.md](../../instructions/chaining-engine.instructions.md)