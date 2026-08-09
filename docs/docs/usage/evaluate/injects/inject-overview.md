# Injects

Injects are the building blocks of security testing in OpenAEV. Each Inject represents a single action (phishing
email, command execution, DNS resolution…) that OpenAEV executes against your infrastructure during a
[Scenario](../../build/scenario/scenario.md) or as a standalone [Atomic test](../atomic-testing/atomic-testing.md).

## Create an Inject

Creating an Inject means defining **what** to execute, **against whom**, and **what outcome to expect**. Every Inject is
powered by an [Injector](../../environment/injectors.md), the connector that knows how to deliver the action (email, agent command,
API call, etc.).

### Benefits

- **Validate defenses**: test whether your security stack prevents or detects a specific technique.
- **Build realistic Scenarios**: combine multiple Injects into a full attack chain.
- **Measure response**: check if your teams react as expected when facing a threat.

### Steps

The creation workflow is the same whether you work from Atomic testing or from a Scenario/Simulation.

#### 1. Open the creation panel

| Context | Where to go |
|---------|-------------|
| Atomic testing | **Atomic testing** in the left menu, then click the **+** button (bottom-right) |
| Scenario / Simulation | Open the Scenario or Simulation > **Injects** tab > click the **+** button (bottom-right) |

![Filtered list of Injects during selection](assets/example_inject_filtering.png)

!!! note

    An Inject defined in a Scenario applies to all subsequent Simulations of that Scenario. An Inject added directly to a Simulation is **not** replicated back into the Scenario.

#### 2. Choose the Inject type

The left panel lists all available Inject types. Each row shows the Injector logo so you can identify the source at a
glance. Use the search bar or filter by [MITRE ATT&CK](https://attack.mitre.org/) technique to narrow the list.

#### 3. Configure the Inject

Select an Inject type on the left. The right panel loads a form with a default title. Fill in:

| Section | What to define |
|---------|---------------|
| **General** | Title, description, tags, and execution timing (for Scenarios/Simulations) |
| **Targets** | [Endpoints and Asset groups](../../build/assets.md) or [Players and Teams](../../build/people.md) |
| **Expectations** | [Expected outcomes](../expectations/expectations.md): prevention, detection, human response |
| **Attachments** | Supporting documents or resources |
| **Inject-specific fields** | Email subject/body, obfuscation options, channel pressure, etc. |

### In practice

You want to test whether your EDR (Endpoint Detection and Response) blocks a `Mimikatz` execution (MITRE ATT&CK T1003):

1. Open **Atomic testing**, then click **+**.
2. Filter by technique **T1003: OS Credential Dumping**.
3. Select the matching command-line Inject.
4. Assign your target Windows Endpoint.
5. Add a **Prevention** expectation.
6. Save and launch.

## Output parsing and results

Some Inject types produce structured output that OpenAEV parses automatically to extract actionable results (CVEs,
vulnerabilities, alerts…) without any manual work. The parsing logic depends on the Injector and is handled by each
integration individually.

Many community and official integrations are available. Check the
[OpenAEV integrations repository](https://github.com/OpenAEV-Platform) for the full list of supported tools and
connectors.

## Conditional execution of Injects

Conditional execution (also called **Inject chaining**) links Injects together so that a child Inject only runs when
specific conditions on its parent are met at execution time. Conditions can be based on
[Expectation](../expectations/expectations.md) results (prevention, detection) or on execution success or failure.

### Why chain Injects?

- **Model real attack chains**: execute lateral movement only if initial access succeeded.
- **Reduce noise**: skip follow-up Injects when a prerequisite was blocked.
- **Test decision trees**: simulate branching attacker behavior depending on defensive outcomes.

### Link Injects from the Inject update form

1. Open an Inject and go to the **Logical chains** tab.
2. Assign a **Parent**. The current Inject only executes if the Parent's conditions are met.
3. Assign **Children**. They execute only if the current Inject's conditions are satisfied.
4. Select the conditions: choose the relevant Expectation and toggle **Success** or **Fail**.
5. Toggle the **AND / OR** operator to control whether all conditions must be met or just one.

!!! note

    The AND/OR setting applies globally to all conditions of the Inject. You cannot mix operators.

### Link Injects from the timeline

1. Switch to the **timeline view** of the Injects list.
2. Hover over the connection point (small dot) on the left or right of an Inject.
3. Drag and drop a link to another Inject.

Links created this way default to the condition **Execution is Success**. Edit them via the Inject update form to set
more specific conditions. You can reposition or remove links by dragging them to an empty area.

### How chained execution works

When a Simulation runs, OpenAEV evaluates each chained Inject at execution time: the platform reads the outcome of the
parent Inject (execution status or Expectation results), applies the AND/OR operator across the configured conditions,
and either executes or skips the child Inject.

Chained [Scenarios](../../build/scenario/scenario.md) extend this model with a full execution workflow. When the
`INJECT_CHAINING` preview feature is enabled, you can create a chained Scenario whose Injects are linked into an
attack-path workflow. The chain definition acts as a template, and OpenAEV creates a workflow run with runtime steps
when the Scenario is launched. Each step moves through a single lifecycle: ready, then running, then ended. Before
starting a child step, OpenAEV evaluates its conditions, which can compare execution results, Expectation values,
mapped output values, or dependencies between steps. When a parent step finishes, OpenAEV records its outputs in the
workflow state and propagates them to dependent steps before evaluating the next condition.

Chained workflow runs behave as follows:

| Behavior | Description |
|----------|-------------|
| Output reuse | Outputs produced during the run (statuses, Expectation results, findings) are stored so later steps can use them in conditions. |
| Asynchronous processing | Steps whose conditions are met are queued for execution, and Inject lifecycle events feed results back into the run. |
| Delays | Time-based waits between steps are scheduled by the platform without blocking execution. |
| Timeouts | A run that exceeds its configured execution window is ended automatically, together with its remaining steps. |

!!! note

    Chained Scenarios and the workflow view depend on the `INJECT_CHAINING` preview feature being enabled on the
    platform (see [Parameters](../../../administration/parameters.md)). The **Logical chains** tab on an Inject is
    always available and does not require the preview feature.

### In practice

You are simulating a multi-stage attack:

1. **Inject 1**: phishing email with a malicious attachment.
2. **Inject 2**: Threat arsenal action execution on the endpoint (child of Inject 1, condition: *Prevention expectation = Fail*).
3. **Inject 3**: lateral movement (child of Inject 2, condition: *Execution = Success*).

If the EDR blocks the attachment (Prevention = Success), Inject 2 and 3 are automatically skipped.

## What's next?

- [Inject tests](inject-tests.md) -- Dry-run email and SMS Injects before launching a Simulation.
- [Inject status](inject-status.md) -- Understand execution traces, trace statuses, and how statuses are computed
- [Expectations](../expectations/expectations.md) -- Define success criteria
- [Findings](../findings/findings.md) -- See parsed results after execution
- [Inject results](inject-result.md): full breakdown of your security posture against a test.
