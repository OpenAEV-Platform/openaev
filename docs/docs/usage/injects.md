# Injects

Injects are fundamental elements of simulations within OpenBAS, each representing a discrete action to be executed during a scenario. Managed and facilitated by various [injectors](injectors.md), each inject type serves a distinct purpose, contributing to the comprehensive evaluation of defenses.

<!-- screenshot of a nice list of various type of inject in the screen "Simulation > [open one simulation] > Injects" -->


## Create an inject

Whether intended for [Atomic testing](atomic.md) or integration into a [Simulation](simulation.md), the process for creating injects remains consistent within OpenBAS.

### For Atomic testing

To create an inject for atomic testing, navigate to the "Atomic testing" section located in the left-hand banner. Click on the "+" icon at the bottom right corner to initiate the inject creation process.

### For Simulations

For injects intended for use within simulations, access the desired simulation and navigate to the "Injects" tab. Click on the "+" icon at the bottom right corner of the screen to open the inject creation panel.

### Inject creation process

Once the inject creation panel is open, you can proceed to configure the inject according to your requirements. Key steps in the creation process include:

1. Choose the type of inject.
2. Set inject parameters:

    - Descriptive information: Fill in details such as the title, description, and relevant tags to categorize the inject effectively. 
    - [Inject targets](targets.md): Specify the targets for the inject, which may include [players and teams](teams_and_players_and_organizations.md) or [assets](assets.md) depending on the inject chosen. 
    - [Expectations](expectations.md): Define the expected outcomes or responses to the inject, outlining the desired actions or behaviors by players. 
    - Attachments: Attach any relevant documents or resources to provide additional context or information related to the inject. 
    - Execution timing: Set the timing for when the inject should be executed within the simulation timeline, ensuring it aligns with the overall scenario progression. 
    - Additional fields: Depending on the type of inject selected, you may have access to additional fields specific to that inject type. These fields may include the subject and body of an email, channel pressure settings for public communications, obfuscation options, and more.

<!-- screenshot of the inject creation panel (i.e. "Simulation > [open one simulation] > Injects > [Click on "+"]") with some information fill -->

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


### Remote actions

Injects executed on remote systems are facilitated by injectors like [Caldera](inject-caldera.md) or Airbus CyberRange. These actions simulate real-world scenarios, allowing administrators to gauge the effectiveness of security measures in response to various threat scenarios.

There are over 1,700 such injects covering all the TTPs in the MITRE matrix.

<!-- screenshot of a nice list of Caldera/Airbus CyberRange inject in the screen "Simulation > [open one simulation] > Injects > [Click on "+"]" -->
