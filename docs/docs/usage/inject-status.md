# Inject status

Every Inject execution produces a **status** that tells you the outcome at a glance: success, failure, or partial
completion. Statuses are computed automatically from the execution traces reported by
[OpenAEV Agents](openaev-agent.md).

## Why it matters

- **Diagnose at a glance**: know immediately whether an Inject worked, failed, or was blocked.
- **Prioritize investigation**: focus on `ERROR` or `PARTIAL` results instead of reviewing every trace manually.

## Execution traces

When an Inject targets [Endpoints](assets.md), each installed Agent reports its progress to the OAEV Server as
**execution traces**, structured log entries for each step of the process:

| Step | Description |
|------|-------------|
| **Prerequisite check** | Validates required conditions before execution |
| **Prerequisite retrieval** | Installs missing prerequisites (only if the check fails) |
| **Attack command** | Executes the actual Payload |
| **Cleanup** | Removes artifacts left by the attack |

!!! warning

    If a prerequisite check succeeds, the retrieval step is skipped. The UI always marks prerequisite checks as "SUCCESS". Inspect the stderr logs to verify actual execution results.

### Where to find traces

| Location | Content |
|----------|---------|
| **Execution details** tab (Inject result page) | Traces for the overall Inject execution |
| **Execution** tab (Inject overview page) | Per-target traces, including Endpoints and individual Agents |

The **Targets** panel (left side) lists every target organized by type (Asset group, Endpoint, Agent, Team, Player).
Only tabs with at least one active target appear. Use pagination and filters to navigate large lists.

## Trace statuses reference

Every execution step reports a **trace status**, grouped into three categories.

### Success statuses

| Status                      | Description | Details |
|-----------------------------|-------------|---------|
| `SUCCESS`                   | Command executed successfully | |
| `SUCCESS WITH CLEANUP FAIL` | Main command succeeded, but cleanup failed | The main command executed successfully, but the cleanup step failed. Check cleanup prerequisites and logs on the target. |
| `WARNING`                   | Command completed with stderr output | The command completed but produced stderr output. Review stderr for potential issues. |
| `ACCESS DENIED`             | Command blocked due to insufficient privileges | The command was denied due to insufficient privileges. This confirms the security control is working: the agent attempted execution but was blocked. |

### Error statuses

| Status                       | Description | Details |
|------------------------------|-------------|---------|
| `ERROR`                      | General execution failure | |
| `COMMAND NOT FOUND`          | Command not found on the target | The command was not found on the target. Ensure the tool is installed and available in the system `PATH`. |
| `COMMAND CANNOT BE EXECUTED` | Command exists but cannot run | The command exists but cannot be executed. Check file permissions and ensure the binary has execute rights. |
| `PREREQUISITE FAILED`        | Prerequisite check failed | A prerequisite check failed before the main command could run. Review prerequisite dependencies and ensure they are met on the target. |
| `INVALID USAGE`              | Incorrect arguments or syntax | The command was invoked with incorrect arguments or syntax. Verify the inject parameters and command. |
| `TIMEOUT`                    | Execution exceeded time threshold | The agent did not complete execution within the allowed time threshold. Consider investigating target performance. |
| `INTERRUPTED`                | Inject interrupted before completion | The inject was interrupted before completion. This may be caused by a system signal, user intervention, or resource constraint. |

### Informational statuses (excluded from status computation)

| Status            | Description | Details |
|-------------------|-------------|---------|
| `AGENT INACTIVE`  | Agent was not active during Inject execution | This agent was not active during the inject execution. Check your asset connectivity. |
| `ASSET AGENTLESS` | Asset has no Agent installed. | |
| `INFO`            | Informational trace (e.g., Agent spawn notification). | |

!!! note "Deprecated statuses"

    `MAYBE PREVENTED`, `PARTIAL`, and `MAYBE PARTIAL PREVENTED` are deprecated.

## Status computation

The OAEV Server aggregates trace statuses in two levels: first per **Agent**, then across Agents to produce the
**Inject status**.

### Agent status

The server evaluates all traces for a single Agent with the following priority rules:

| Priority | Condition | Resulting status                                                    |
|----------|-----------|---------------------------------------------------------------------|
| 1 | Any non-cleanup, non-prerequisite trace is an error | That specific error status (or `ERROR` if multiple distinct errors) |
| 2 | A prerequisite failed | `PREREQUISITE FAILED`                                               |
| 3 | Execution succeeded but cleanup failed | `SUCCESS WITH CLEANUP FAIL`                                         |
| 4 | All traces succeeded | `SUCCESS`                                                           |

### Inject status

The server computes the Inject-level status from per-Agent COMPLETE traces, **excluding `AGENT INACTIVE` Agents**:

| Condition | Inject status |
|-----------|--------------|
| All active Agents succeeded | <span style="color: #4caf50">**SUCCESS**</span> |
| All active Agents errored | <span style="color: #f44336">**ERROR**</span> |
| Mix of success and error | <span style="color: #ff9800">**PARTIAL**</span> |
| No active Agents | <span style="color: #f44336">**ERROR**</span> |

## In practice

You run an Inject targeting three Endpoints, each with one OpenAEV Agent:

| Agent | What happened | Agent status                |
|-------|--------------|-----------------------------|
| **Agent A** | Attack command succeeds, cleanup fails | `SUCCESS WITH CLEANUP FAIL` |
| **Agent B** | Attack command blocked by EDR | `ACCESS DENIED`             |
| **Agent C** | Agent was offline | `AGENT INACTIVE` (excluded) |

One success + one error among active Agents → Inject status: **PARTIAL**.

## Alert details

After execution, OpenAEV retrieves alert data from connected security platforms (SIEM, EDR) so you can correlate Inject
activity with real detections.

![Inject execution traces details](assets/inject-expectation-traces-1.png)

Select an Agent in the **Targets** panel to view its detection traces. Click the Agent name to expand traces. If a
detection was identified on an external platform, click the alert name to open it directly in that platform.

![Inject execution traces alert details](assets/inject-expectation-traces-2.png)

!!! warning

    Detection data can take several minutes to appear in OpenAEV after Inject execution.

## Adding manual results

When automated result retrieval is not possible (e.g., non-technical Injects), record results manually:

1. Open the Inject result page.
2. Click the **shield** icon labeled **Add a result**.
3. Fill in the result form and save.

![Adding a manual result](assets/inject-expectation-manual-result-1.png)
![Adding a manual result popup](assets/inject-expectation-manual-result-2.png)

## Go further

- Define [Expectations](expectations/overview.md) to set success criteria for your Injects.
- Explore [Findings](findings.md) to see what was detected during execution.
- Review [Inject results](inject-result.md) for a full breakdown of your security posture against a test.

