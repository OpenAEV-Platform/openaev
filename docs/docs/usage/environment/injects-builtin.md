# Built-in Injects

OpenAEV ships with several built-in Injectors that are always available without installing external components. These Injectors cover the most common Simulation needs: email delivery, manual actions, media pressure, challenges, and endpoint execution.

## Why use built-in Injectors?

- Start running Simulations immediately without deploying external integrations.
- Cover both technical (endpoint execution) and non-technical (email, media pressure) Inject types out of the box.
- Use the OpenAEV Implant for native command execution on endpoints without requiring third-party agents like Caldera.

## Email Injector

The email Injector (`openaev_email`) sends emails to Players during a Simulation. It requires an SMTP service to be configured on the platform.

Two contracts are available:

| Contract | Description |
|---|---|
| Send individual mails | Sends a separate email to each targeted Player. Supports email obfuscation. |
| Send multi-recipients mail | Sends a single email to all targeted Players at once. |

Each email Inject supports the following fields:

- **Subject**: email subject line
- **Body**: rich text content with variable substitution
- **Attachments**: optional file attachments
- **Encrypted**: toggle to enable email encryption

## Manual Injector

The manual Injector (`openaev_manual`) creates Injects that require human execution outside of the platform. Use manual Injects for actions that cannot be automated, such as physical security tests or phone-based social engineering.

Manual Injects have no required fields. Their outcome is tracked through expectations that operators validate manually.

## Channel Injector (media pressure)

The channel Injector (`openaev_channel`) publishes articles to Teams as simulated media pressure. Use it to simulate news coverage, data breach announcements, or internal communications during a Simulation.

The "Publish channel pressure" contract requires:

- A **Channel** template defining the visual appearance (logo, branding)
- One or more **Articles** with title, author, and content

Published articles appear in the Player interface during the Simulation. See [Media pressure](../build/components/media-pressure.md) for details on creating articles and channels.

## Challenge Injector

The challenge Injector (`openaev_challenge`) embeds CTF (Capture The Flag)-style security challenges into a Simulation. Players must solve the challenge to validate the associated expectation.

## OpenAEV Implant

The OpenAEV Implant Injector (`openaev_implant`) executes commands on endpoints through the OpenAEV Agent. It provides native execution capabilities without requiring external tools like Caldera.

See [OpenAEV Agent](openaev-agent.md) for details on agent architecture and deployment.

## What's next?

- [Inject overview](../evaluate/injects/inject-overview.md) -- How to create and configure Injects
- [Media pressure](../build/components/media-pressure.md) -- Create articles and channels
- [OpenAEV Agent](openaev-agent.md) -- Deploy and use the native agent
