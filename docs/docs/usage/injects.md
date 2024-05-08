# Injects

Injects are fundamental elements of simulations within OpenBAS, each representing a discrete action to be executed during a Scenario. Managed and facilitated by various [injectors](injectors.md), each inject type serves a distinct purpose, contributing to the comprehensive evaluation of defenses.

![Injects list in a Scenario](assets/injects_list_in_scenario.png)


## Create an inject

Whether intended for [Atomic testing](atomic.md) or for a [Simulation](simulation.md), the process for creating injects remains consistent within OpenBAS.

![Capture of a filtered list of inject during selection process](assets/example_inject_filtering.png)

### For Atomic testing

To create an inject for atomic testing, navigate to the "Atomic testing" section located in the left-hand banner. Click on the "+" icon in the bottom right corner to initiate the inject creation process.

### For Scenarios and Simulations

For injects intended for use within simulations, access the desired Scenario or Simulation and navigate to the "Injects" tab. Click on the "+" icon in the bottom right corner of the screen to open the inject creation panel.

Note that an inject defined in a Scenario will be used in all the scenario's subsequent simulations. An Inject defined at the simulation level will not be replicated into the Scenario itself, thus it will not be replicated in future scenario's simulations.

<a id="inject-creation-section"></a>
### Inject creation process

Once the inject creation panel is open, you can proceed to configure the inject according to your requirements. Key steps in the creation process include:


#### 1. Choose the type of inject

You first need to select an inject in the list of available ones (on the left of the creation screen). Logos on the left of each line indicates which Injector is associated with each [inject](injects.md). Depending on your integrations, this list can be long.

To facilitate the selection into this possibly very long list, you can search injects by name and filter the list by selecting a precise MITRE ATT&CK techniques for instance.


#### 2. Set inject parameters

When selecting an inject on the left, the form on the right populates itself with a by-default title and propose you to define:

- Descriptive information: Fill in details such as the title, description, and relevant tags to categorize the inject effectively.
- Execution timing: If you are creating your inject in the context of a scenario or simulation, you have to set the timing for when the inject should be executed within the simulation timeline, ensuring it aligns with the overall scenario progression.

By clicking on "Inject content", you can define now or later:

- [Inject targets](targets.md): Specify the targets for the inject, which may include [players and teams](teams_and_players_and_organizations.md) or [assets and assets groups](assets.md) depending on the inject chosen. 
- [Expectations](expectations.md): Define the expected outcomes or responses to the inject, outlining the desired actions or behaviors by players. 
- Attachments: Attach any relevant documents or resources to provide additional context or information related to the inject. 
- Additional fields: Depending on the type of Inject selected, you may have access to additional fields specific to that inject type. These fields may include the subject and body of an email, channel pressure settings for public communications, obfuscation options, and more.

The "available variables" button helps you to use already defined variables into compatible fields.

![screenshot of the inject creation panel](assets/email_inject_definition.png)

By following these steps and providing the necessary information, you can create injects tailored to your specific testing or simulation objectives.


## Inject types

There are different types of injector in OpenBAS.

<a id="manual-section"></a>
### Manual action reminders

Manual action reminders are injects designed to prompt animation team to perform specific actions manually. It allows to place in the timeline a stimulus to be produced manually, outside the platform (e.g. simulated a call from a journalist on the switchboard telephone). These reminders ensure that critical tasks are completed as part of the simulation, enhancing the accuracy and realism of the exercise.

The inject associated with this type is referred to as `Manual`.


### Direct contact

Injects for direct contact allow sending emails or SMS messages to players. These injects assess the organization's response to communication-based threats, such as phishing attempts, social engineering attacks, or emergency notifications. They can also assess crisis management, including responses to internal information requests or management pressure.

Here's the list of injects linked to this category:

- `Send a SMS`: enables sending SMS messages.
- `Send individual mails`: enables sending emails to individuals separately.
- `Send multi-recipients mail`: enables sending emails to a group of people (each recipient can see the other recipients).


<a id="media-pressure-section"></a>
### Media pressure

Injects simulating public communications involve the publication of articles, social media posts, or other fake announcements. These injects replicate scenarios where public disclosure of information or events affects an organization's reputation or operational continuity.

The inject associated with this type is referred to as `Publish channel pressure`.


<a id="challenge-section"></a>
### Challenges

Challenge injects are set to test participants' skills and response capabilities by publishing challenges. These injects present scenarios or tasks that require active participation and problem-solving, allowing administrators to evaluate players.

The inject associated with this type is referred to as `Publish challenges`.


<a id="http-section"></a>
### HTTP requests

HTTP request injects are used to forge HTTP requests to a third party services in order to perform actions outside the platform (e.g. API call to an EDR). These injects enable the platform to communicate with external services, gather information, or trigger specific actions via HTTP protocols.

HTTP requests GET, POST, and PUT, can be sent. The corresponding injects are named `HTTP Request - \<request type>`.


<a id="integration-section"></a>
### Integrations with Agents and CyberRanges

Injects executed on remote systems are facilitated by Injectors like [Caldera](inject-caldera.md) or Airbus CyberRange. These actions simulate real-world attack techniques, allowing administrators to gauge the effectiveness of their security posture in response to various technical actions attackers may take.

There are over 1,700 such injects covering all the TTPs in the MITRE ATT&CK matrix.

