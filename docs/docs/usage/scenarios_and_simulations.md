# Scenarios and Simulations

In OpenBAS, the core concept to simulate attacks is based on the duo [Scenario](scenario.md) & [Simulation](simulation.md).

## Scenarios

Scenario enable to translate a threat context, such as an attack or even a threat actor, into a meaningful sequence of events (referred to as [injects](injects.md)), which can be technical or non-technical. This chronology of events can be enriched with associated documents or media articles to simulate the environment surrounding them.

Within Scenarios, you also specify who participates, whether actual people (referred to as [Players](people.md)) or endpoints (referred to as [Assets](assets.md)). They will be the targets of the events representing the threat.

In order to translate real threats into Scenarios, it is possible to create them from OpenCTI data, such as Reports.


## Simulations

If a Scenario translates threat context into meaningful events, a Simulation serves as a means to evaluate your security posture against this threat context. 

By simulating a scenario with recurrence, you can evaluate your security posture over time in response to a threat context. Since simulations are always linked to their parent scenario, even if it evolves, you can:
- assess your risk against evolving threats,
- evaluate the effectiveness of your security governance in addressing your most relevant threats.

<!-- Here a screenshot of a Scenario with multiple Simulations with different results percentage -->
