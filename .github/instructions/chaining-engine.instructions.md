---
applyTo: |
  openaev-api/src/main/java/io/openaev/api/chaining/**,
  openaev-api/src/main/java/io/openaev/service/chaining/**,
  openaev-api/src/main/java/io/openaev/scheduler/jobs/QueueChainingJob.java,
  openaev-api/src/main/java/io/openaev/service/InjectChainingCondition.java
---

# Chaining Feature — Copilot Instructions

## Overview

The **Chaining** feature enables automated, conditional execution of steps within a simulation workflow.
It orchestrates inject sequences based on events, conditions, outputs, and time constraints.

The full execution flow is driven by a **Step Queue + Job + Pool** architecture.

---

## Architecture & Flow

### High-level flow (from Figma design)

```
Creation of Simulation
  └─> Creation of Step Template
        └─> Creation of Workflow Template
              └─> Launch Simulation
                    └─> [For each step template]
                          └─> Conditions Event Valid?
                                ├─ no  → END
                                └─ yes → Update Local Pool
                                           └─> Conditions Mapper Valid?
                                                 ├─ no  → END
                                                 └─ yes → Input Already Executed?
                                                           ├─ yes → Check Hash Input / Save Input
                                                           └─ no  → Creation Next Step(s) Execution
                                                                      └─> Queue Ready
                                                                            └─> [consume] Job Fetches Ready Steps
                                                                                  └─> Inject Creation + Execution
                                                                                        └─> Callback Event
                                                                                              └─> API Call by External Sys / Human
                                                                                                    └─> Save output as global
                                                                                                          └─> Update Step Run Outputs
                                                                                                                └─> Update Global Pool
                                                                                                                      └─> Check & Get Step Template that needs this output type
                                                                                                                            └─> [Time Condition?]
                                                                                                                                  ├─ yes → System Delay → Push Step Template
                                                                                                                                  └─ no  → [End Step Execution?]
                                                                                                                                              ├─ yes → END
                                                                                                                                              └─ no  → loop
```

### Key concepts

| Concept | Description |
|---|---|
| **Step Template** | Blueprint for a step in a workflow. Defines conditions, events, and expected outputs. |
| **Step Run** | Runtime instance of a Step Template during a simulation execution. |
| **Workflow** | Ordered graph of Step Templates linked by conditions and outputs. |
| **Global Pool** | Shared state holding all outputs produced during a simulation run. Used to resolve conditions. |
| **Local Pool** | Temporary state scoped to a step evaluation cycle. |
| **Condition** | Logical rule evaluated against the pool to determine if a step should proceed. |
| **Event** | Trigger that initiates or resumes step execution evaluation. |
| **Queue** | Message queue (RabbitMQ) used to dispatch and consume step execution jobs. |
| **Scope** | Asset targeting rules that define which assets a step applies to. |

---

## Package Structure

```
io.openaev.api.chaining/           ← API layer (controllers, mappers, DTOs)
  ├── ChainingApi.java             ← REST endpoints for chaining
  ├── ConditionApi.java            ← REST endpoints for conditions
  ├── StepApi.java                 ← REST endpoints for steps
  ├── ConditionMapper.java         ← MapStruct mapper for Condition entity ↔ DTO
  ├── StepMapper.java              ← MapStruct mapper for Step entity ↔ DTO
  ├── DataOutputStep.java          ← Data class for step output
  └── dto/
        ├── ChainingOutput.java
        ├── ConditionOutput.java
        ├── EventOutput.java
        ├── WorkflowOutput.java
        ├── WorkflowScopeRuleInput.java
        ├── WorkflowScopeRuleOutput.java
        └── ScopeAssetOutput.java

io.openaev.service.chaining/       ← Business logic layer
  ├── QueueChainingService.java    ← Manages queue interactions for chaining
  ├── StepService.java             ← Core step lifecycle management
  ├── StepEventService.java        ← Handles step event processing
  ├── StepEventHandler.java        ← Listens and dispatches step events
  ├── StepEvent.java               ← Event model for step transitions
  ├── ScopeService.java            ← Asset scope resolution
  ├── WorkflowStateService.java    ← Manages workflow run state
  └── ExternalUpdateEventHandler.java ← Handles callbacks from external systems / humans

io.openaev.service/
  └── InjectChainingCondition.java ← Condition evaluation at inject level

io.openaev.scheduler.jobs/
  └── QueueChainingJob.java        ← Scheduled job that fetches and processes ready steps
```

---

## Coding Rules

- Always use **MapStruct** for entity ↔ DTO conversions (see `ConditionMapper`, `StepMapper`).
- Services must be **stateless** — all state lives in the Global/Local Pool or the database.
- Queue interactions must go through `QueueChainingService` — never publish directly to RabbitMQ from a service.
- Conditions are evaluated **before** any step execution — never skip condition validation.
- Step status transitions must follow: `READY` → `RUN` → `END` (or `FINAL_STATUS`).
- When a step output is saved globally, always call `updateGlobalPool()` before triggering downstream step resolution.
- Time conditions must use `SystemDelay` — never use `Thread.sleep()`.
- External callbacks (human or system) go through `ExternalUpdateEventHandler`.

---

## Step Status Reference

| Status | Shape (Figma) | Meaning |
|---|---|---|
| `READY` | Orange arrow | Step is ready to be executed |
| `READY → RUN` | Green arrow | Step is running |
| `RUN + END` | Blue arrow | Step completed successfully |
| `FINAL_STATUS` | Purple/Orange/Green/Blue | Terminal state of a step |

---

## Related Files

- `QueueChainingJob.java` — entry point for scheduled step processing
- `StepEventHandler.java` — entry point for event-driven step processing
- `InjectChainingCondition.java` — condition evaluation tied to inject lifecycle