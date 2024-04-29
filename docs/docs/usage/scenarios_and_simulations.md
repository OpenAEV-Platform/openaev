# Scenarios and Simulations

!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).

In OpenBAS, the core concept to simulate attacks is based on the duo Scenario & Simulation.

## Scenarios

Scenario allows to translate a threat context, like an attack or even a threat actor, into a meaningful chronology of events (called injects), technical or not. This chronology of events can be detailed with associated documents or media articles to also simulate the environment around these events.

In Scenarios, you also define who is playing, either actual people (called Players) or endpoints (called Assets). They will be targets of the events representing the threat.

In order to translate real threat into Scenario, it is possible to create them from OpenCTI data, like Reports.

## Simulations

If a Scenario represents threat context translated into meaningful events, a Simulation is the mean to evaluate your security posture against this threat context. 

Simulating a Scenario with reccurence allows you to evaluate your security posture over time against a threat context, and because Simulations are always related to their parent Scenario, even if it evolved, you will be able to:
- evaluate your risk facing evolving threats.
- evaluate the impact of your security governance against your most relevant threats.

<!-- Here a screenshot of a Scenario with multiple Simulations with different results percentage -->
