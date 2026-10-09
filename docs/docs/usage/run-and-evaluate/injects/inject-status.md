# Inject status

Every Inject execution produces a **status** that tells you the outcome at a glance: success, failure, or partial
completion. Statuses are computed automatically from the execution traces reported by
[OpenAEV Agents](../../../integrations/agents/openaev-agent.md).

!!! warning "Execution status vs. Expectation results"

    The **Execution Status** strictly reflects the *operational state* of the action (e.g., did the command run, crash, or time out). It exists **only to give the user visibility into the technical execution** of the Injector.

    Execution Status **does not** represent your security posture. To evaluate if an attack was actually detected or prevented by your security controls, you must refer to **Expectation Results**, which gather data directly from OpenAEV Collectors.

## Why it matters

- **Diagnose at a glance**: know immediately whether an Inject worked, failed, or was blocked.
- **Prioritize investigation**: focus on `ERROR` or `PARTIAL` results instead of reviewing every trace manually.

## Execution traces

When an Inject targets [Endpoints](../../build/assets.md), each installed Agent reports its progress to the OAEV Server as
**execution traces**, structured log entries for each step of the process:

| Step | Description |
|------|-------------|
| **Prerequisite check** | Validates required conditions before execution |
| **Prerequisite retrieval** | Installs missing prerequisites (only if the check fails) |
| **Attack command** | Executes the actual Threat Arsenal Action|
| **Cleanup** | Removes artifacts left by the attack |

!!! note

    If a prerequisite check succeeds, the retrieval step is skipped. The UI always marks prerequisite checks as "SUCCESS". Inspect the stderr logs to verify actual execution results.

### Where to find traces

| Location | Content |
|----------|---------|
| **Execution details** tab (Inject result page) | Traces for the overall Inject execution |
| **Execution** tab (Inject overview page) | Per-target traces, including Endpoints and individual Agents |

The **Targets** panel (left side) lists every target organized by type (Asset group, Endpoint, Agent, Team, Player).
Only tabs with at least one active target appear. Use pagination and filters to navigate large lists.

## Trace statuses reference

Every execution step reports a **trace status**. Below is the complete list of actionable execution statuses, divided into logical categories to help operators troubleshoot technical issues.


### Successful executions

| Status                      | Description | Details |
|-----------------------------|-------------|---------|
| `EXECUTED`                   | Command executed to completion without system errors | *Note: This only means the command ran, not that it bypassed defenses* |
| `EXECUTED WITH CLEANUP FAIL` | Main command succeeded, but cleanup failed | Check if the action locked the file or if permissions changed during execution |
| `WARNING`                   | Command completed but produced stderr output | Review stderr logs for potential non-blocking issues |
| `ACCESS DENIED`             | Command denied by the OS due to insufficient privileges | Check if the agent is running with the required rights (e.g., Admin/Root) |

### Error statuses

| Status                       | Description | Details |
|------------------------------|-------------|---------|
| `ERROR`                      | General, unclassified execution failure | Check the agent logs for detailed stack traces |
| `COMMAND NOT FOUND`          | Executable or binary missing on the target system | Ensure dependencies (e.g., `curl`, `powershell`) are installed in the `PATH` |
| `COMMAND CANNOT BE EXECUTED` | Command exists but cannot run | Check file execute permissions (`chmod +x`) or architecture compatibility |
| `PREREQUISITE FAILED`        | A prerequisite check failed before the main command | Review prerequisite dependencies and ensure they are met on the target |
| `INVALID USAGE`              | Incorrect arguments or syntax | The command was invoked with incorrect arguments or syntax Verify the inject parameters and command |
| `TIMEOUT`                    | No result received within the execution threshold | The Agent never reported back to OpenAEV. See [Execution time limits](#execution-time-limits) |
| `INTERRUPTED`                | Inject interrupted before completion | This may be caused by a system signal, user intervention, or resource constraint |

### Informational statuses (excluded from status computation)

| Status            | Description | Details |
|-------------------|-------------|---------|
| `AGENT INACTIVE`  | Agent was not active during Inject execution | This agent was not active during the inject execution. Check your asset connectivity |
| `ASSET AGENTLESS` | Asset has no Agent installed | Install an OpenAEV agent on the target asset |
| `INFO`            | Informational trace (e.g., Agent spawn notification) | |

!!! note "Deprecated statuses"

    `MAYBE PREVENTED` and `MAYBE PARTIAL PREVENTED` are deprecated. OpenAEV no longer sets them: an Agent that does not report back in time gets a `TIMEOUT` trace.

## Execution time limits

OpenAEV applies two separate time checks to an Inject.

### Late start: "too old for execution"

When a scheduled Inject starts later than its planned time plus a grace period, OpenAEV does not run it. The Inject ends
in `ERROR` with the trace message `Inject is now too old for execution`, followed by the Inject ID, its launch date and
the current date.

- The grace period is 4 minutes by default. Set it with `openaev.scheduling.inject-staleness-threshold` (minimum 1). See
  [Configuration](../../../reference/deployment/configuration.md#inject-execution).
- This check applies to Injectors built into the platform, such as Email, SMS and the OpenAEV implant.
- It happens when the platform could not start the Inject in time, for example after a restart or under heavy load.

### Execution threshold: `TIMEOUT`

Once an Inject is sent, OpenAEV waits for each targeted Agent to report back. The wait is set by
`INJECT_EXECUTION_THRESHOLD_MINUTES` (10 minutes by default). When it expires:

- Each Agent that did not report gets a `TIMEOUT` trace: `Agent <user> did not respond within the <n> minutes threshold.`
- An Inject without Agents gets a `TIMEOUT` trace: `The inject did not complete within the <n> minutes threshold and was marked as timed out.`
- OpenAEV computes the final Inject status (`ERROR` or `PARTIAL`).

The OpenAEV implant started on the endpoint is also stopped after this delay.

### `TIMEOUT` on an Asset shown as active

The Agent status and the `TIMEOUT` trace measure different things:

- **Active** means the Agent was seen in the last hour. For EDR-based Executors, this last-seen date comes from the EDR
  platform, not from OpenAEV.
- **`TIMEOUT`** means the implant on the endpoint never sent its result to OpenAEV.

An Asset can therefore be active and still time out. Check that:

1. The endpoint can reach the OpenAEV URL (network, proxy, firewall, TLS certificate).
2. No antivirus or EDR blocks or deletes the implant.
3. The Executor delivered the command. See [Executors troubleshooting](../../../integrations/executors/executors.md#troubleshooting).

## Status computation hierarchy

In OpenAEV, the execution status is not a simple average of agents. The platform computes the final status by bubbling up the results through the architectural hierarchy:

1. **Agent level:** The atomic execution result on a specific endpoint (e.g., `SUCCESS` or `BLOCKED_BY_EDR`).
2. **Asset level:** Aggregates the status of all agents running on that specific asset.
3. **Asset Group level:** Aggregates the status of all assets within the targeted group.
4. **Inject level:** The final global status displayed in the UI, aggregating all targeted asset groups and direct assets.

### Agent status computation

The server evaluates all traces for a single Agent with the following priority rules:

| Priority | Condition | Resulting status                                                    |
|----------|-----------|---------------------------------------------------------------------|
| 1 | Any non-cleanup, non-prerequisite trace is an error | That specific error status (or `ERROR` if multiple distinct errors) |
| 2 | A prerequisite failed | `PREREQUISITE FAILED`                                               |
| 3 | Execution succeeded but cleanup failed | `EXECUTED WITH CLEANUP FAIL`                                         |
| 4 | All traces succeeded | `EXECUTED`                                                           |

### Inject status computation

The server computes the Inject-level status from per-Agent COMPLETE traces, **excluding `AGENT INACTIVE` Agents**. However, if *no* agents were active, the execution fails:

| Condition | Inject status |
|-----------|--------------|
| All active Agents succeeded | **EXECUTED** |
| All active Agents errored | **ERROR** |
| Mix of success and error | **PARTIAL** |
| No active Agents | **ERROR** (execution cannot succeed without at least one active Agent) |

## In practice

You run an Inject targeting three Endpoints, each with one OpenAEV Agent:

| Agent | What happened | Agent status                |
|-------|--------------|-----------------------------|
| **Agent A** | Attack command succeeds, cleanup fails | `SUCCESS WITH CLEANUP FAIL` |
| **Agent B** | Attack command blocked by EDR | `ACCESS DENIED`             |
| **Agent C** | Agent was offline | `AGENT INACTIVE` (excluded) |

One success + one error among active Agents →  The status are mixed, so the final Inject status is **PARTIAL**

## What's next?

- [Expectations](../expectations/expectations.md) -- Define success criteria for your Injects
- [Findings](../findings/findings.md) -- Explore what was detected during execution
- [Inject result](inject-result.md) -- Full breakdown of your security posture against a test

