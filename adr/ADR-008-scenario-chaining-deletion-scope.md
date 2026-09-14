# ADR-008: Scenario chaining deletion is TEMPLATE-scoped only, never cascades to the Simulation

|         |                                                        |
|---------|--------------------------------------------------------|
| Status  | Accepted                                               |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/XXX |

## 1. Context

A chaining-enabled `Scenario` owns exactly one `Workflow` row with status **TEMPLATE**
(`workflow_scenario_id` set, `workflow_simulation_id` NULL), itself composed of `Step` TEMPLATEs,
`Condition`s attached to those steps, and `WorkflowScopeRule`/`ScopeVariable` rows describing the
configured scope. This row is never anything other than a TEMPLATE — a Scenario's workflow is
never a RUN.

Launching a simulation from that scenario does **not** run the scenario's own TEMPLATE workflow
directly. `WorkflowService.launchWorkflowScenario` / `startWorkflowByScenarioIdAndSimulation`
first **copies** the scenario's TEMPLATE (its configuration, scope rules, scope variables, steps
and conditions — via `copyWorkflowTemplateToSimulation` + `stepService.copyStepTemplate`) into a
**brand new** `Workflow` row, itself TEMPLATE but scoped to the simulation
(`workflow_simulation_id` set instead of `workflow_scenario_id`). Only then is the RUN `Workflow`
created from *that* simulation-scoped TEMPLATE copy (`copyWorkflowTemplateToRun`), and its
`workflowTemplate` FK points to the simulation-scoped copy — **never** to the scenario's own
TEMPLATE row.

This ADR documents the existing, already-implemented behavior of deleting such a scenario
(verified via `ChainingIntegrationTest`), so it is treated as an intentional contract rather than
an accident of JPA cascade configuration that a future refactor could silently break.

## 2. How it works today

`ScenarioService.deleteScenario()` does not contain any imperative cleanup logic. It resolves to
a single `scenarioRepository.deleteById(scenarioId)` call, and the entire cascade scope is driven
by **database-level foreign key constraints** (declared in Flyway migrations), not by a Hibernate
object-graph relation — `Scenario` has no JPA-mapped reference to `Workflow` at all:

```java
@Transactional(rollbackFor = Exception.class)
public void deleteScenario(@NotBlank final String scenarioId) {
  existsByIdAndTenantId(scenarioId);
  this.scenarioRepository.deleteById(scenarioId); // cascade fully driven by DB FK constraints below
}
```

**What gets deleted in cascade (the scenario's own TEMPLATE subtree):**

| Relation | Mapping | Effect |
|---|---|---|
| `workflows.workflow_scenario_id` → `scenarios.scenario_id` | DB FK `ON DELETE CASCADE` (`V4_91__Add_scenario_to_workflow.java`) | The scenario's TEMPLATE workflow row deleted with the scenario |
| `steps.step_workflow_id` → `workflows.workflow_id` | DB FK `ON DELETE CASCADE` (`V4_72__Add_workflow_step_entities.java`) + JPA `CascadeType.ALL`/`orphanRemoval` (`Workflow.java` L85-93) | Step TEMPLATEs of that workflow deleted |
| `conditions.step_id` → `steps.step_id` | DB FK `ON DELETE CASCADE` (`V4_72__Add_workflow_step_entities.java`) + JPA `CascadeType.ALL`/`orphanRemoval` (`Step.java` L128-136) | Condition TEMPLATEs deleted |
| `workflow_scope_rules.workflow_id` → `workflows.workflow_id` | DB FK `ON DELETE CASCADE` (`V4_80__Add_workflow_configuration.java`) + JPA `CascadeType.ALL`/`orphanRemoval` (`Workflow.java` L159-166) | Scope Rules TEMPLATE deleted |
| `scope_variables.scope_variable_workflow` → `workflows.workflow_id` | DB FK `ON DELETE CASCADE` (`V5_02__Add_scope_variables.java`) + JPA `CascadeType.ALL`/`orphanRemoval` (`Workflow.java` L189-196) | Scope variables deleted |

**Why the Simulation (and its own TEMPLATE + RUN workflows) is never affected:**

Unlike a foreign-key/nullable-reference situation, the simulation-side workflows are not
*referencing* the scenario's TEMPLATE row at delete time — they are **independent copies** made
once, at launch time, of its configuration, steps and conditions. Once launched, the simulation's
TEMPLATE and RUN workflows (`workflow_simulation_id` set) live entirely under the `Exercise`, with
no live foreign key back to the scenario's TEMPLATE row at all. Deleting the scenario therefore
has nothing left to reach on the simulation side, by construction, not because a reference gets
nulled out.

(The `workflows.workflow_template_id → workflows.workflow_id` foreign key — the one a RUN uses to
point at *its own* TEMPLATE — is declared `ON DELETE SET NULL` in `V4_72__Add_workflow_step_entities.java`.
This matters for a standalone, non-scenario simulation, where a RUN's `workflowTemplate` points
directly at a persisted TEMPLATE row of that same simulation. It is irrelevant to scenario
deletion: a scenario-launched RUN's `workflowTemplate` points at the simulation-scoped TEMPLATE
copy described above, never at the scenario's own TEMPLATE row, so this FK never fires as a
consequence of deleting a scenario.)

In other words: the deletion boundary is drawn exactly at the scenario/simulation frontier.
Everything owned by the scenario's own TEMPLATE workflow (steps, conditions, scope rules, scope
variables) is deleted along with it. Everything on the simulation side (its own TEMPLATE, its RUN,
and the `Exercise` itself) is a separate, self-contained copy and is left untouched.

## 3. Verification (existing tests)

- `ChainingIntegrationTest.should_delete_scenario_template_entities_but_keep_simulation_intact`
  (`openaev-api/src/test/java/io/openaev/service/chaining/ChainingIntegrationTest.java:353`)
  creates a chaining scenario, adds a Step TEMPLATE with its Conditions, attaches a
  `WorkflowScopeRule` and a `ScopeVariable` to the scenario's TEMPLATE workflow, launches a
  simulation from it (creating the simulation's own TEMPLATE copy and RUN workflow), deletes the
  scenario, then asserts: the `Scenario` is gone, the scenario's own TEMPLATE `Workflow` is gone,
  the Step TEMPLATEs are gone, the `Condition`s attached to those steps are gone, the
  `WorkflowScopeRule`s are gone, the `ScopeVariable`s are gone, and — critically — **the
  `Exercise` (Simulation), and the RUN workflow it launched, still exist**.
- `ScenarioServiceTest.shouldNullReferencesFromSecurityCoverageAndSimulationsWhenScenarioDeleted`
  covers the non-chaining case: a simulation linked to a deleted scenario survives, with its
  `scenario` reference nulled.

All five entity kinds owned by the scenario's own TEMPLATE workflow (`Workflow`, `Step`,
`Condition`, `WorkflowScopeRule`, `ScopeVariable`) are explicitly asserted as deleted, and the
simulation's RUN `Workflow` / `Exercise` are explicitly asserted as preserved. No coverage gap
remains.

## 4. Consequences

### Positive

- A scenario can be freely deleted/cleaned up after use without any risk of destroying past
  simulation results.
- No orphaned TEMPLATE rows: steps, conditions, scope rules and scope variables of a deleted
  scenario's workflow are all removed, avoiding indefinite table growth.
- Zero imperative cleanup code to maintain in `ScenarioService`: the contract is declarative,
  enforced by the database itself (FK constraints) and mirrored in the JPA mappings where
  Hibernate also needs to manage the object graph, reducing the risk of a future service change
  silently breaking it.

### Negative / trade-offs

- The contract is split across Flyway migration files (DB-level FK constraints) and JPA entity
  annotations rather than living in one explicit, named service method — a developer auditing
  "what does deleting a scenario delete" must read both the relevant migrations (`V4_91`, `V4_72`,
  `V4_80`, `V5_02`) and the entity mappings (`Workflow`, `Step`) rather than one method. Mitigated
  by this ADR and by the existing integration test acting as executable documentation.

### Neutral

- No change to the public API surface (`ScenarioApi#deleteScenario`) or to the `DELETE
  /api/scenarios/{scenarioId}` contract.
- The test previously named `should_delete_scenario_and_cascade_to_simulation_steps_and_workflows`
  (whose name implied the Simulation was deleted, contradicting its own assertion) has been
  renamed to `should_delete_scenario_template_entities_but_keep_simulation_intact` and extended
  with `Condition`/`WorkflowScopeRule`/`ScopeVariable` assertions, closing the documentation and
  coverage gaps noted above. Pure test change, no behavior change.
