# Getting started

!!! tip "Under construction"

    We are doing our best to complete this page. If you want to participate, don't hesitate to join the [Filigran Community on Slack](https://community.filigran.io) or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).

This guide aims to give you a full overview of the OpenBAS features and workflows. The platform can be used in various contexts to handle Breach and Attack simulations at technical or strategical levels. OpenBAS has been designed as a part of the Filigran XTM suite and can be integrated with [OpenCTI](https://filigran.io/solutions/open-cti/) to generate meaningful attack scenario based on real threat. OpenBAS is result-oriented with many dashboard helping you to evaluate you securoty posture given a defined context.

Here are some examples of use cases:
- Designing attack scenario based on real threat
- Evaluate your security posture against technical simulations on endpoints
- Enhance team skills by evaluating them during simulations along with your security systems
- Organize Capture The Flag with multiple challenges
- Conduct atomic testing

## Home screen

The Home screen gives any visitor on the OpenBAS platform an outlook on the live of the platform as well as an overview of your global security posture.


## Your first Breach and Attack Simulation


### Importing players and assets to play with
First, you need to import Players and Assets that will participate to the simulation and be targeted by technical or strategical events. To do so, you need to ...

### Integrate with Simulation Agent to simulate technical events
If you want to simulate attack at a technical level, you will need to install a Simulation Agent that will play technical events on your imported assets. To do so, you can go to the dedicated panel from the top right button and follow installation instructions. By default, Caldera is part of the OpenBAS stack.

![Simulation Agents screen](assets/simulation_agents_screen.png)

The [Caldera agent](inject-caldera.md) will allow you to play various technical events, based on the Mitre ATT&CK matrix.

### Building your Scenario
Once integrations is done, you are ready to create your first Scenario! 

[Scenarios](scenario.md) act as template for your Breach and Attack simulations. After establihing such a template, you will be able to schedule it as a one shot simulation, or as a recurring one. 

- First, go to the Scenarios menu and create a new one with the + button.
- Once done, define [Teams](teams_and_players_and_organizations.md) that will be playing in this Scenario by going to the Definition/Teams tab
- Now go to the [Injects](injects.md) tab and add some to build the serie of events that will define the core of your Scenario. If you want to stay strategical, you can select inject like "Send individual mails". If you want to go technical, you can select injects linked to attack pattern (Caldera integration allows you to play hundreds of them).
- Then, define [who or what will be targeted](targets.md) by those injects, customize them, and define what is expected to happen. For example, you expect the targeted team to perform a specific action and the animation team will validated this expectation manually. Or, you expect the technical event to be prevented and it will be automatically checked through your integrations with your security systems.
- Do not forget to define when the inject is played in the scenario chronology.

Optionnaly, you can buff up your scenario by adding [Documents](documents.md), [Media pressures](media_pressure.md), or even CTF [Challenges](challenges.md) to your injects.

### Play the simulation

You can now schedule your [Simulation](simulation.md) by hitting the blue "Simulate now" button. Choose your moment and hit start.

On time, a Simulation based on your Scenario template is generated. it is listed in your Scenario overview and in the Simulations menu. Fron there, you can follow the course of the Simulation and interact with it, for example to validate manual expectations.

During the course of the simulation, results are updated and can be consulted in the Simulation overview.


### Evaluate your security posture

Results in OpenBAS are based on expectations' results that are linked to injects played during Simulations. It is then important to manually validate expectations that need it.

Results are broken down by "Prevention", "Detection" and "Human response" metrics.
- Prevention displays your ability to prevent the scenario's technical events to be completed
- Detection displays your ability to detect the scenario's technical events
- HUman response displays how well players and teams react as expected facing the scenario's events.
