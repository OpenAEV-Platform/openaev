# Executors

Executors run implants as detached processes on your endpoints, and the implants run the Threat Arsenal Actions. You need at least one Executor to run Actions on endpoints. Executors work on Windows, Linux and macOS, on x86_64 or ARM64.

| Executor                           | Type          | Installation mode                                 | Run as                                 | Threat Arsenal Action execution                | Multiple agents per endpoint                     |
|:-----------------------------------|:--------------|:--------------------------------------------------|:---------------------------------------|:-----------------------------------------------|:-------------------------------------------------|
| [**OpenAEV agent (native/default)**](../agents/openaev-agent.md) | Open source   | Windows/Linux: user session, user service or system service<br/>macOS (temporary): system service only | A standard or admin background process | As a user standard, user admin or system admin | Yes, depending on the user and installation mode |
| [**Tanium agent**](tanium.md)                   | Under license | As a system service                               | An admin background process            | As a system admin                              | No, always the same agent                        |
| [**CrowdStrike Falcon agent**](crowdstrike.md)       | Under license | As a system service                               | An admin background process            | As a system admin                              | No, always the same agent                        |
| [**SentinelOne agent**](sentinelone.md)              | Under license | As a system service                               | An admin background process            | As a system admin                              | No, always the same agent                        |
| [**Palo Alto Cortex agent**](palo-alto-cortex.md)         | Under license | As a system service                               | An admin background process            | As a system admin                              | No, always the same agent                        |
| [**Microsoft Defender for Endpoint (MDE) agent**](mde.md) | Under license | Reuses the existing MDE sensor (Live Response) | An admin background process (SYSTEM) | As a system admin                     | No, always the same agent                        |
| [**Caldera agent**](caldera.md)                  | Open source   | As a user session                                 | An admin background process            | As a user admin                                | Yes, depending on the user                       |

!!! tip "Enterprise Edition"

    The Tanium, CrowdStrike, SentinelOne, Palo Alto Cortex and MDE Executors require an Enterprise Edition license. Without it, their agents show an **EE** badge and Injects targeting them are refused with a `LICENSE RESTRICTION` trace.

<a id="deploy-agents"></a>

## Configure an Executor

1. Configure the Executor's platform, as described in its page.
2. In OpenAEV, open **Integrations**, select the Executor and fill in its settings.
3. Check that the Executor appears on the **Install simulation agents** page, and that its endpoints appear in **Assets > Endpoints**.

!!! note "Migrating from environment variables"

    If you configured an Executor with environment variables or platform properties, these values were migrated to the database on first startup. Since then, OpenAEV ignores them: manage the configuration from the UI.

!!! note "One agent per endpoint"

    With the Tanium, CrowdStrike, SentinelOne and Palo Alto Cortex Executors, an endpoint has a single agent, because OpenAEV identifies it by its MAC (Media Access Control) address. Installing a new agent replaces the existing one.

## OpenAEV agent

The OpenAEV agent is the native and default way to run implants and Threat Arsenal Actions on Windows, Linux and macOS. See [OpenAEV agent](../agents/openaev-agent.md).

## Third-party Executors

<a id="tanium-agent"></a>
<a id="crowdstrike-falcon-agent"></a>
<a id="paloaltocortex-agent"></a>
<a id="sentinelone-agent"></a>
<a id="mde-agent"></a>
<a id="microsoft-defender-for-endpoint"></a>
<a id="microsoft-defender-for-endpoint-troubleshooting"></a>
<a id="caldera-agent"></a>

- [Tanium](tanium.md) -- Run implants through Tanium packages
- [CrowdStrike Falcon](crowdstrike.md) -- Run implants through Real Time Response scripts
- [Palo Alto Cortex](palo-alto-cortex.md) -- Run implants through the Agent Script Library
- [SentinelOne](sentinelone.md) -- Run implants through Remote Ops scripts
- [Microsoft Defender for Endpoint (MDE)](mde.md) -- Run implants through Live Response
- [Caldera](caldera.md) -- Run implants through Caldera agents

## Implant directories and cleanup

This applies to all Executors except Caldera and the OpenAEV agent.

### Implant directories

Implants are downloaded into a `runtimes/implant-XXXXX` subdirectory, where `XXXXX` is unique for each Inject execution. The `runtimes` folder is `C:\Program Files (x86)\Filigran\OAEV Agent\runtimes` on Windows and `/opt/openaev-agent/runtimes` on Linux and macOS.

### Cleanup

The platform sends a cleanup command to endpoints, which removes the `runtimes/` and `payloads/` directories older than **24 hours**:

- Tanium, CrowdStrike and Palo Alto Cortex: every `clean-implant-interval` (default: **8 hours**);
- SentinelOne: every day at 03:00;
- MDE: before each Inject.

!!! note "OpenAEV agent"

    The OpenAEV agent has its own cleanup with different thresholds. See [Cleanup configuration](../agents/openaev-agent.md#cleanup-configuration).

## Troubleshooting

When an Inject fails on an endpoint with an EDR (Endpoint Detection and Response)-based Executor (CrowdStrike, Palo Alto Cortex, etc.):

1. Run the Inject from OpenAEV and wait until it fails or times out.
2. In the Executor console, look for a trace of the execution. If there is none, the problem is upstream: connectivity, API credentials, agent registration in the Executor platform, or Executor configuration.
3. Copy the command OpenAEV pushed, and decode it from Base64. Check the paths for the target OS, the parameters, and escaping or encoding issues.
4. Run the decoded command directly on the endpoint, and keep the full output: it is the most useful information to diagnose the failure.

### `TIMEOUT` while the Asset is active

An active Agent and a `TIMEOUT` trace measure different things:

- **Active** means the Agent was seen in the last hour. For EDR-based Executors, this last-seen date comes from the EDR platform.
- **`TIMEOUT`** means the implant never sent its result to OpenAEV within `INJECT_EXECUTION_THRESHOLD_MINUTES` (10 minutes by default).

If the steps above show that the command ran, check that the endpoint can reach the OpenAEV URL (network, proxy,
firewall, TLS certificate) and that no antivirus or EDR blocks or deletes the implant. See
[Inject status](../../usage/run-and-evaluate/injects/inject-status.md#execution-time-limits).

## What's next?

- [OpenAEV agent](../agents/openaev-agent.md) -- Install the native agent
- [MDE Executor](mde.md) -- Run implants through Microsoft Defender for Endpoint
- [Inject status](../../usage/run-and-evaluate/injects/inject-status.md) -- Understand Inject results and timeouts
