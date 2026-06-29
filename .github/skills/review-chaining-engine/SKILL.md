# Skill: Chaining Feature

## Purpose

This skill guides Copilot when working on the **Chaining** feature of OpenAEV.
It covers adding new steps, conditions, pool interactions, queue jobs, and event handlers.

---

## When to use this skill

- Adding a new **Step type** or **Condition type**
- Modifying the **Global Pool** update logic
- Adding a new **event handler** (step event or external callback)
- Creating or modifying **DTOs** in the chaining API layer
- Debugging step execution flow or queue processing

---

## Step-by-step: Add a new Condition type

1. **Define the condition model** — add the new type in the condition entity or enum.
2. **Update `InjectChainingCondition`** — add evaluation logic for the new type.
3. **Add DTO** — create `NewConditionOutput.java` in `io.openaev.api.chaining.dto`.
4. **Update mapper** — extend `ConditionMapper` with the new type mapping.
5. **Expose via API** — add endpoint in `ConditionApi` if needed.
6. **Write tests** — unit test the condition evaluation in isolation.

---

## Step-by-step: Add a new Step event handler

1. **Define the event** — add or extend `StepEvent` with the new event type.
2. **Implement handler logic** — add a new method or class in `io.openaev.service.chaining`.
3. **Register the handler** — wire it in `StepEventHandler`.
4. **Update pool** — if the event produces output, call `updateGlobalPool()` via `WorkflowStateService`.
5. **Queue dispatch** — if the event triggers a new step, publish via `QueueChainingService`.

---

## Step-by-step: Add a new API endpoint for chaining

1. Add the method in `ChainingApi.java` or the relevant `*Api.java` file.
2. Create input/output DTOs in `io.openaev.api.chaining.dto`.
3. Add mapper methods in `ChainingMapper` or the relevant `*Mapper.java`.
4. Implement service logic in `io.openaev.service.chaining`.
5. Follow REST conventions already used in `ChainingApi` (path, HTTP method, response codes).

---

## Key invariants to never break

- ✅ Conditions are always evaluated before step execution.
- ✅ Global Pool is always updated after a step output is saved.
- ✅ Step status follows: `READY → RUN → END`.
- ✅ Queue interactions go only through `QueueChainingService`.
- ✅ MapStruct is used for all entity ↔ DTO mappings.
- ✅ Time conditions use `SystemDelay`, never `Thread.sleep()`.

---

## Useful links

- [Chaining PRs](https://github.com/OpenAEV-Platform/openaev/pulls?q=is%3Apr+is%3Aopen+chaining+draft%3Afalse)
- Figma design: Step Queue + Job + Pool flow