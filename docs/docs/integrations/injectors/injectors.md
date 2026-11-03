# Injectors

Injectors push simulation actions to endpoints, people and third-party systems. This page lists the main types. To see the Injectors you can deploy, open **Integrations** and the **Available** tab, or browse the [injectors repository](https://github.com/OpenAEV-Platform/injectors).

![List of Injectors](assets/list-of-injectors.png)

## Built-in and external Injectors

- **Built-in Injectors** ship with the platform: email, SMS, media pressure, challenges, phishing, manual actions, OpenCTI and the OpenAEV Implant. See [Built-in Injectors](injects-builtin.md).
- **External Injectors** are separate Python services, for example HTTP query, Nmap, Nuclei, NetExec, Shodan, Slack or Teams. See [Deploy Injectors](deploy-injectors.md).

## Endpoint execution

The OpenAEV Implant runs Threat Arsenal Actions on endpoints. It needs an agent on each endpoint, either the [OpenAEV agent](../agents/openaev-agent.md) or a third-party agent through an [Executor](../executors/executors.md). Supported platforms are Windows, Linux and macOS.

## Communication and social media

These Injectors reach Players: emails, SMS, media pressure articles, challenges, phishing campaigns and manual action reminders. See [Challenges](../../usage/build/components/challenges.md) and [Media pressure](../../usage/build/components/media-pressure.md).

## Others

Other Injectors act on third-party systems, for example HTTP requests, network scans or an OpenCTI platform for [Scenario generation from security coverage](../../usage/build/scenario/security-coverage.md).

## What's next?

- [Built-in Injectors](injects-builtin.md) -- Injectors available without deployment
- [Deploy Injectors](deploy-injectors.md) -- Deploy and configure external Injectors
- [OpenAEV agent](../agents/openaev-agent.md) -- Install the agent on your endpoints
