# Injectors

!!! tip "Injectors list"

    You are looking for the available injectors? The list is in the [OpenAEV Ecosystem](https://filigran.notion.site/OpenAEV-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Introduction

Injectors are one of the cornerstones of the OpenAEV platform, they are responsible for pushing simulation actions to
third party systems. According to their functionality and use case, they are categorized in the following classes.

![List of Injectors](../assets/list-of-injectors.png)

### Endpoint Threat Arsenal Actions execution

Those injectors are special as they required an executor (neutral agent) to be launched on endpoints. When they register
to the platform, they inform available executors on how to spawn them on the 3 currently supported platforms: Windows,
Linux and MacOS.

Some of them :

- Caldera: Facilitates the use of the MITRE Caldera framework, empowering administrators to leverage advanced simulation
  capabilities.

### Communication & social media

Those injectors are used to push information to human assets (aka players) such as emails, SMS, phone calls, instant
messaging etc.

Some of them :

- Challenges: Manages inject "publish challenges". See [Challenges](../build/components/challenges.md) for details.
- Email: Manages the sending of injects' emails, enabling communication and dissemination of simulation-related
    information.
- Manual: Platform functionality for creating manual action reminders, allowing administrators to prompt specific
  actions to be performed manually.
- Media pressure: Manages inject "publish channel pressure". See [Media pressure](../build/components/media-pressure.md) for details.
- OVHCloud SMS Platform: Facilitates SMS messaging for injects, providing an additional communication channel for
  simulation participants.

### Incident response & case management

Those injectors are used to inject real or fake information into case management, ticketing and incident response
systems.

### Others

All other system OpenAEV can inject, as part of breach and attack simulation campaigns.

Some of them :

- Airbus CyberRange (Lade): Integration with the Airbus CyberRange platform, enabling seamless interaction and
  collaboration with CyberRange environments. For more information concerning CyberRange, please refer to
  the [Airbus website](https://www.cyber.airbus.com/cyberrange/).
- HTTP query: Executes HTTP requests on external services, facilitating interactions with external systems.
- OpenCTI: Integration with an OpenCTI platform, enhancing simulation capabilities with access to threat intelligence
  and [automatic scenario generation](../build/scenario/security-coverage.md) based on observed threat activities.

## Agents

!!! tip "Tips"

      If you want to learn more about the concept and features of agents, you can have more info [here](openaev-agent.md).

For certain injectors, deploying an agent on the target machine is necessary to facilitate integration with OpenAEV.
These agents are software programs that connect back to OpenAEV at certain intervals to get instructions.

To access the agents and installation instructions, navigate to the dedicated page located in the top right-hand
corner (button with the screen logo).

Detailed guidance on installing the agents, along with downloadable packages, is provided on this page. Agents are
available for various operating systems, including Windows, Linux, and MacOS, ensuring compatibility across different
environments.

As of now, only the Caldera injector requires the installation of an agent. This agent enables full integration with the
MITRE Caldera framework, unlocking advanced simulation capabilities and enhancing the overall effectiveness of
simulation exercises. Full details of
the [Caldera agent](https://caldera.readthedocs.io/en/latest/Learning-the-terminology.html#agents) are available in the
MITRE documentation.
