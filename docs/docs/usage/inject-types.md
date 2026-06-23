# Inject types

There are different types of injector in OpenAEV.

<a id="manual-section"></a>

## Manual action reminders

Manual action reminders are injects designed to prompt animation team to perform specific actions manually. It allows to
place in the timeline a stimulus to be produced manually, outside the platform (e.g. simulated a call from a journalist
on the switchboard telephone). These reminders ensure that critical tasks are completed as part of the simulation,
enhancing the accuracy and realism of the exercise.

The inject associated with this type is referred to as `Manual`. To be able to log events not directly related to an
email or a sms, you can attach manual expectation to this events (
see [Manual Expectations](https://docs.openaev.io/latest/usage/expectations/?h=manual#manual-expectations)).

### Example of a manual inject:

- "A crisis cell has been put together"

### Manual expectations:

- "The team responded to the journalist's inquiry."
- "Analyze the situation and identify the key issues."
- "Anticipate future developments."
- "Determine the necessary actions to take."
- "Coordinate and communicate with both internal and external stakeholders."
- "Provide regular updates on the ongoing situation."

## Direct contact

Injects for direct contact allow sending emails or SMS messages to players. These injects assess the organization's
response to communication-based threats, such as phishing attempts, social engineering attacks, or emergency
notifications. They can also assess crisis management, including responses to internal information requests or
management pressure.

Here's the list of injects linked to this category:

- `Send a SMS`: enables sending SMS messages.
- `Send individual mails`: enables sending emails to individuals separately.
- `Send multi-recipients mail`: enables sending emails to a group of people (each recipient can see the other
  recipients).

<a id="media-pressure-section"></a>

## Media pressure

Injects simulating public communications involve the publication of articles, social media posts, or other fake
announcements. These injects replicate scenarios where public disclosure of information or events affects an
organization's reputation or operational continuity.

The inject associated with this type is referred to as `Publish channel pressure`.

<a id="challenge-section"></a>

## Challenges

Challenge injects are set to test participants' skills and response capabilities by publishing challenges. These injects
present scenarios or tasks that require active participation and problem-solving, allowing administrators to evaluate
players.

The inject associated with this type is referred to as `Publish challenges`.

<a id="http-section"></a>

## HTTP requests

HTTP request injects are used to forge HTTP requests to third-party services in order to perform actions outside the
platform (e.g. API call to an EDR). These injects enable the platform to communicate with external services, gather
information, or trigger specific actions via HTTP protocols.

HTTP requests GET, POST, and PUT, can be sent. The corresponding injects are named `HTTP Request - \<request type>`.

<a id="integration-section"></a>

## Technical Injects via the OpenAEV Agent

Technical Injects execute commands and threat arsenal actions directly on target Endpoints through the
[OpenAEV Agent](openaev-agent.md). They simulate real-world attack techniques, allowing you to gauge the effectiveness
of your security posture against the actions an attacker would take.

## Agentless Injectors

Some Injects do not require an Agent on the target. Injectors like Nmap or Nuclei run scans and attacks remotely,
targeting endpoints by IP or hostname without any installed agent.

The full list of available Injectors (agent-based and agentless) is maintained in the
[OpenAEV Injectors repository](https://github.com/OpenAEV-Platform/injectors).
