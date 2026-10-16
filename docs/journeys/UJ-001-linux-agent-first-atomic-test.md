---
id: UJ-001
title: Install the Linux agent on an endpoint and run a first atomic test
status: draft
owner:
persona: [SOC analyst, Platform admin]
priority: P0
edition: [CE]
doc_pages:
  - integrations/agents/openaev-agent.md
  - integrations/agents/linux.md
  - usage/build/assets.md
  - usage/build/threat-arsenals/threat-arsenals.md
  - usage/run-and-evaluate/atomic-testing/atomic-testing.md
  - usage/run-and-evaluate/injects/inject-status.md
last_checked:
---

## Overview

- **What**: deploy the OpenAEV agent on a Linux host and prove the attack loop end to end: agent, implant, execution, result.
- **Who**: platform admin setting up OpenAEV, SOC analyst running the first test.
- **Goal**: a first atomic test executed on a real endpoint, with its output in the platform.

## Prerequisites

- A Linux host with systemd and a `/etc/machine-id`.
- The host reaches the platform at the address set in `openaev.base-url`.

## Steps

1. Install the agent with the Linux command from the agent installation screen.
2. Check that the endpoint appears in **Assets** with an active agent.
3. Create or pick a Linux Command payload in the **Threat Arsenal**.
4. Create an atomic test with the OpenAEV Implant injector, targeting the endpoint, and launch it.
5. Follow the Inject status until **Executed** and read the output.

## Expected result

Inject status **Executed**, with the command output in the execution details.

## Doc gaps

- The `machine-id` and `base-url` reachability prerequisites are not documented.
- Most built-in implant payloads target Windows; the docs do not say a Linux payload has to be created.

## Completion criteria

A new user completes the steps from the docs only.
