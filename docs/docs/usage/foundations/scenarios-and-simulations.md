# Scenarios and Simulations

In OpenAEV, the core workflow for adversary simulation is based on the duo Scenario and Simulation. A Scenario defines what to test, and a Simulation executes it to measure your security posture.

## Scenarios

A Scenario translates a threat context, such as an attack campaign or a threat actor, into a meaningful sequence of events called [Injects](../evaluate/injects/inject-overview.md). These events can be technical (endpoint commands, network scans) or non-technical (phishing emails, phone calls, media pressure).

Within a Scenario, you specify:

- **Injects**: the chronological sequence of actions to execute
- **Targets**: who participates, whether [Players](../build/people.md) (people) or [Assets](../build/assets.md) (endpoints)
- **Context**: supporting [documents](../build/components/documents.md), [media articles](../build/components/media-pressure.md), and [challenges](../build/components/challenges.md)

Scenarios can be created manually or generated from OpenCTI data such as Reports. See [Generating Scenarios from OpenCTI](../build/scenario/security-coverage.md).

## Simulations

If a Scenario defines the threat, a Simulation evaluates your security posture against it. Each Simulation is a concrete execution of a Scenario that produces measurable results.

By running Simulations with recurrence, you can track your security posture over time. Since Simulations are always linked to their parent Scenario, even as the Scenario evolves, you can:

- Assess your risk against evolving threats
- Measure the effectiveness of security improvements over successive runs
- Demonstrate compliance and readiness through historical results

## What's next?

- [Scenario](../build/scenario/scenario.md) -- Create and manage Scenarios
- [Simulation](../evaluate/simulation/simulation.md) -- Run and monitor Simulations
- [Inject overview](../evaluate/injects/inject-overview.md) -- Define the events in a Scenario
- [Getting started](../getting-started.md) -- Platform usage overview
