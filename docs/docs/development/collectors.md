# Collector development

This page explains what OpenAEV expects from a Collector: a process that reads the security events of a detection or prevention product, such as an **EDR (Endpoint Detection and Response)** or a **SIEM (Security Information and Event Management)**, and matches them against the expectations of the Injects that OpenAEV executed.

To set up a new Collector project (template, `pyoaev` dependency, local development), follow the [Collectors repository README](https://github.com/OpenAEV-Platform/collectors#development). For a functional overview, see [Collectors](../integrations/collectors/collectors.md).

## How a Collector works with OpenAEV

1. **Register.** The Collector registers through `POST /api/collectors` and appears under **Integrations > Deployed**.
2. **Read the expectations.** At each run, it fetches the expectations it has not answered yet. Answer them quickly to measure how fast the product responds.
3. **Match and report.** It compares each expectation's signatures with the product's alerts, then updates the expectation with the result: prevented, detected, or not.

You do not need to fail late expectations yourself: the expectation expiration job of OpenAEV marks unanswered expectations as expired after a timeout (see [Platform managers](../deployment/platform/managers.md#scheduled-jobs)).

## Signatures

A signature is a way to find an attack in an alert. The main ones are:

| Signature | Description |
|---|---|
| `parent_process_name` | Name of the parent process of the attack. For the OpenAEV implant, it is `oaev-implant-<inject_id>-agent-<agent_id>` (the file itself has `.exe` on Windows). |
| `source_ipv4_address`, `source_ipv6_address` | IP address of the Asset that ran the attack. |
| `target_ipv4_address`, `target_ipv6_address`, `target_hostname_address` | IP address or hostname targeted by the attack. |

![Collector diagram](assets/collector-diagram.png)

For reference implementations, see the [CrowdStrike](https://github.com/OpenAEV-Platform/collectors/tree/main/crowdstrike), [Microsoft Defender](https://github.com/OpenAEV-Platform/collectors/tree/main/microsoft-defender) and [Microsoft Sentinel](https://github.com/OpenAEV-Platform/collectors/tree/main/microsoft-sentinel) Collectors.

## What's next?

- [Deploy Collectors](../integrations/collectors/deploy-collectors.md) -- Deploy and configure a Collector
- [Expectations](../usage/run-and-evaluate/expectations/expectations.md) -- Understand the expectations a Collector answers
- [Injector development](injectors.md) -- Build a custom Injector
