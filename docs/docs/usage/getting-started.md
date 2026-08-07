# Getting started

OpenAEV lets you validate your security posture by simulating real-world adversary techniques. OpenAEV is part of the Filigran XTM suite and integrates with [OpenCTI](https://filigran.io/solutions/open-cti/) to generate meaningful attack Scenarios based on real threats.

This guide introduces the key concepts and workflows behind the platform.

## The workflow

A typical OpenAEV workflow follows six steps:

1. **Define your targets** -- Register the [Assets](build/assets.md) (endpoints) and [People](build/people.md) (Players, Teams) you want to test.
2. **Prepare your actions** -- Browse or create [Threat Arsenal Actions](build/threat-arsenals/threat-arsenals.md): the technical or non-technical actions that will be executed (shell commands, phishing emails, DNS resolutions, etc.).
3. **Build Injects** -- Wrap each Action into an [Inject](evaluate/injects/inject-overview.md) by specifying the target, the schedule, and the expected outcome ([Expectations](foundations/injects-and-expectations.md)).
4. **Assemble a Scenario** -- Combine Injects into a [Scenario](build/scenario/scenario.md): a reusable attack sequence that tells the story of a threat.
5. **Run a Simulation** -- Execute the Scenario as a [Simulation](evaluate/simulation/simulation.md) to measure your security posture. Run it once or schedule it for recurrence.
6. **Analyze results** -- Review outcomes across four axes (prevention, detection, vulnerability, human response) in [Dashboards](evaluate/dashboards/custom-dashboards.md) and drill into individual [Findings](evaluate/findings/findings.md).

You can also skip Scenarios entirely and run standalone [Atomic Tests](evaluate/atomic-testing/atomic-testing.md) to validate a single technique in isolation.

## Starter pack

OpenAEV ships with a starter pack that provides ready-to-use content so you can run your first Simulation immediately after installation. The starter pack includes:

| Content | Description |
|---|---|
| Pre-built Scenarios | Tabletop, agentless, and agent-based Scenarios covering common attack techniques |
| Dashboards | Four Dashboards for monitoring prevention, detection, and response metrics |
| Injectors | Nmap and Nuclei Injectors for network and vulnerability scanning |
| Collectors | Atomic Red Team, MITRE ATT&CK, and CVE/NVD (National Vulnerability Database) feeds |
| Agentless endpoint | One pre-configured endpoint with an Asset group |

The starter pack is available from the XTM Hub. Import it from **Settings > XTM Hub** after registering your platform.

## What's next?

- [Scenarios and Simulations](foundations/scenarios-and-simulations.md) -- Understand the Scenario/Simulation model
- [Inject overview](evaluate/injects/inject-overview.md) -- Create and configure Injects
- [Threat Arsenal](build/threat-arsenals/threat-arsenals.md) -- Manage Actions and Payloads
- [Assets](build/assets.md) -- Register endpoints and Asset groups
- [People](build/people.md) -- Manage Players and Teams
