---
name: Feature request
about: Suggest a new feature or capability for OpenAEV
title: 'refactor(execution): phase external inject path into short scoped transactions'
labels: needs triage, feature
assignees: ''
type: feature

---

## Use case

The external inject execution path (CrowdStrike/Tanium/PaloAlto) should keep tenant-scoped transactions short and release DB connections before RabbitMQ publish. This reduces pool contention risk and removes commit-after-publish ambiguity for status traces.

## Current workaround

Current execution uses one broad scoped transaction around most of the flow, including parts that should be separated from publish. It works functionally, but it keeps a connection longer than needed under concurrent load.

## Proposed solution

External path — 3 phases

Phase 1 — all DB prep (one transaction)
`InjectsExecutionJob.executeInject():`
- `checkErrorMessagesPreExecution()` // DB read

`Executor.execute():`
- `injectorRepository.findByTypeAndTenantId()` // DB read (fallback only)
- `connectorInstanceService.hasStartedConnectorInstance()` // DB read
- `injectStatusService.initializeInjectStatus()` // DB write -> EXECUTING
- `executionExecutorService.launchExecutorContext()` // heavy DB: agents, traces

`Executor.executeExternal():`
- `injectStatusRepository.findByInjectId()` // DB read
- `injectService.resolveAllAssetsToExecute()` // DB read
- `injectExpectationService.computeAndSaveExpectations()` // DB write <- last DB op

// COMMIT, connection released

Phase 2 — no transaction, no connection
- `rabbitmqService.publish(injectorId, jsonInject)`

Phase 3 — one short transaction
- `injectStatus.addInfoTrace("published and waiting...")`
- `injectStatusRepository.save(injectStatus)` // DB write

// COMMIT, connection released

Internal path (OpenAEV agent, synchronous in-process) can remain a single scoped transaction.

## Additional information

- Scope propagation remains v2-correct: both activated tables are still accessed inside scoped transactions (`executors` and `collectors`).
- `InjectsExecutionJob` should pass `TxCtx.forTenant(tenantId)` down the chain and stop owning one broad transaction.
- `launchExecutorContext` currently includes `launchBatchExecutorSubprocess` network I/O; splitting that method into DB phase + network phase should be tracked separately.
- Suggested acceptance checks:
  - external path uses 3-phase boundaries as described above,
  - status trace `"published and waiting..."` is saved in the post-publish short transaction,
  - internal path behavior remains unchanged.

## If the feature request is approved, would you be willing to submit a PR?

No
