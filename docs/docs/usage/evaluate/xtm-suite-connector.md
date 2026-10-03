# Security Coverage enrichment (XTM Suite)

OpenAEV enables other products from the XTM Suite to benefit from a comprehensive Security Coverage enrichment for a given Adversarial Exposure scenario.
This means that OpenAEV can be triggered via an XTM Suite product to execute a scenario based on a desired threat profile, and results from the scenario execution — such as Detection rate, Prevention rate — are returned to the triggering product for ingestion.

## What is this?

The XTM Suite connector allows OpenAEV to communicate bidirectionally with other Filigran products. When enabled, OpenAEV exposes its validation results (detection rate, prevention rate, etc.) to the connected product, providing automated security posture assessments.

This feature is currently available for the following product:

* **OpenCTI** — Threat intelligence platform

## Why use it?

- **Automated feedback loop**: Security Coverage results from OpenAEV simulations are automatically pushed back to OpenCTI, enriching threat intelligence with real validation data.
- **Continuous posture assessment**: Trigger scenario executions directly from OpenCTI to validate your defenses against specific threats.
- **Unified visibility**: View detection and prevention rates alongside threat intelligence in a single platform.

## Automated enrichment for OpenCTI

### Prerequisites

This feature requires:

1. An active **OpenCTI instance** (v6.x or later recommended). Refer to the [OpenCTI documentation](https://docs.opencti.io/latest/) for deployment instructions.
2. The **OpenAEV platform** configured and running with at least one scenario ready to execute.

Once the OpenCTI instance is up and running, gather the following information:

| Item | Description |
|:-----|:------------|
| OpenCTI URL | The instance's full domain name (e.g., `https://opencti.domain.example`) |
| API Token | A valid API token with sufficient privileges (see [Configuring the Connector API token](https://docs.opencti.io/latest/deployment/connectors/#connector-token)) |

### How to enable the connector

#### Step 1: Configure OpenAEV to connect to OpenCTI

Each OpenCTI connection is scoped to an OpenAEV **tenant**, identified by its UUID (`{id}`). This allows each tenant in a [multi-tenant deployment](../../administration/multi-tenancy.md) to have its own OpenCTI integration.

Set the following parameters in your OpenAEV deployment:

```properties
openaev.xtm.opencti.{id}.enable=true
openaev.xtm.opencti.{id}.url=https://opencti.domain.example
openaev.xtm.opencti.{id}.token=<your-opencti-api-token>
```

Or as environment variables:

```properties
OPENAEV_XTM_OPENCTI_{id}_ENABLE=true
OPENAEV_XTM_OPENCTI_{id}_URL=https://opencti.domain.example
OPENAEV_XTM_OPENCTI_{id}_TOKEN=<your-opencti-api-token>
```

!!! tip "What is `{id}`?"

    The `{id}` is the **OpenAEV tenant UUID** (e.g., `2cffad3a-0001-4078-b0e2-ef74274022c3`). You can find it in the platform administration under tenant settings.

!!! tip "API URL override"

    You only need to set `api_url` if your GraphQL endpoint differs from the default `<url>/graphql` (e.g., behind a reverse proxy with a custom path).

#### Step 2: Restart OpenAEV

After updating the configuration, restart the OpenAEV platform for the changes to take effect.

#### Step 3: Verify the connector in OpenCTI

The connector is now up and running and should be visible in OpenCTI as **OpenAEV Coverage**.

![Active OpenAEV Coverage connector in OpenCTI](../assets/active_openaev_connector_in_opencti.png)

### Trigger security coverage enrichments from OpenCTI

Once the connector appears in OpenCTI, you can trigger it to run security coverage enrichments. Refer to the [OpenCTI documentation](https://docs.opencti.io/latest/) for how to trigger the enabled connector to get automated enriched security posture assessments with OpenAEV.

## Hunt validation from emulation results

OpenCTI hunts search your SIEM, EDR or data lake for the techniques of a threat. OpenAEV can prove whether those hunts actually catch a technique: every emulation of the technique by a simulation is a known-true event, so OpenAEV asks OpenCTI to run its hunts over the time the emulation ran, on the security platform that watched it. OpenCTI then records whether the hunts found it as a `hunt_detected` coverage result on the Security Coverage, next to the detection and prevention rates.

This feature requires OpenCTI **Enterprise Edition** with hunts and a hunt connector bound to the security platform. It is **disabled by default**.

### How it works

1. A simulation generated from an OpenCTI Security Coverage runs. Its injects emulate ATT&CK techniques (the Attack Patterns of their Threat Arsenal action) on your assets.
2. The security platforms connected through collectors (EDR, XDR, SIEM, SOAR, NDR, ISPM) give their verdict on the detection and prevention expectations of each inject, or the expectations expire.
3. Once the simulation results are pushed back to OpenCTI, OpenAEV plans one hunt validation per **inject, technique and security platform** whose verdict is computed. A triple is planned once only, however many times the results are pushed again.
4. A background job sends each validation to OpenCTI (`huntValidateFromEmulation`) with:
    - the technique ATT&CK ID (for example `T1059.001`),
    - the security platform, by the STIX ID the simulation results give it (derived from its name, see [the security platform identities](../../reference/apis/security-coverage-results.md)) and by its name, which OpenCTI falls back to,
    - the inject ID,
    - the inject execution window, from the time the inject was sent to the time it completed, widened by a padding (5 minutes by default) on both sides,
    - the OpenCTI Security Coverage ID of the simulation.
5. OpenCTI runs its active hunts covering the technique on the hunt connector of that security platform, over that window, and writes the outcome on the coverage.

Only injects that ran (status `Executed` or `Partial`) are validated. Platform types OpenCTI hunts cannot run on (email security, AI defense, vulnerability scanners) are skipped.

### Enable it

The validation uses the OpenCTI connection of each tenant configured in [Step 1](#step-1-configure-openaev-to-connect-to-opencti), and is enabled for the whole platform:

```properties
openaev.security-coverage.hunt-validation.enabled=true
```

Or as an environment variable:

```properties
OPENAEV_SECURITY-COVERAGE_HUNT-VALIDATION_ENABLED=true
```

See the [configuration reference](../../deployment/configuration.md#xtm-suite-opencti-hunt-validation) for the window padding, the request timeout, the batch size and the number of attempts.

### Failures

A hunt validation never blocks the simulation results: they are pushed to OpenCTI first, and the validations are sent by a separate job.

- When OpenCTI refuses a validation (Enterprise Edition not enabled, unknown technique, OpenCTI version without hunts), OpenAEV logs one warning per tenant and per run, and retries after 5, 10, 20 and 40 minutes before giving the validation up (5 attempts by default).
- When OpenCTI cannot be reached or answers a server error, the job stops sending for that tenant until its next run (every minute) and postpones the validation by 5 minutes without counting an attempt: an outage, however long, never makes OpenAEV give a validation up.

## Example workflow

1. A threat analyst identifies a new intrusion set in **OpenCTI**.
2. The analyst triggers the **OpenAEV Coverage** connector on the associated Security Coverage object.
3. OpenAEV receives the request, maps it to an existing scenario, and executes the simulation.
4. Results (detection rate, prevention rate, findings) are pushed back to OpenCTI as enrichment data.
5. When hunt validation is enabled, OpenAEV asks OpenCTI to run its hunts over each emulated technique, and OpenCTI adds whether the hunts caught it to the coverage.
6. The analyst sees the updated security posture directly in the OpenCTI interface.

## Results sent back to OpenCTI

The results are sent as a STIX 2.1 bundle. For each covered object (attack pattern, vulnerability, indicator or
artifact), the bundle carries the overall detection and prevention scores, and the scores attributed to each security
platform that reported on it (`coverage_platforms`), so OpenCTI shows which EDR (Endpoint Detection and Response), SIEM
(Security Information and Event Management) or other security platform detected or prevented each technique. The [Security Coverage result bundle](../../reference/apis/security-coverage-results.md) reference
documents every object and property of the bundle.

## What's next?

- [Scenario Generation from OpenCTI Security Coverage](../build/scenario/security-coverage.md) — Automatically create OpenAEV scenarios from OpenCTI Security Coverage objects.
- [Configuration reference](../../deployment/configuration.md#xtm-suite-opencti) — Full list of configuration parameters.
- [Security Coverage result bundle](../../reference/apis/security-coverage-results.md) — Objects, properties and scores sent back to OpenCTI.
- [Scenarios and Simulations](../foundations/scenarios-and-simulations.md) — Understand how scenarios and simulations work in OpenAEV.
