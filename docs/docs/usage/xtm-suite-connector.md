# XTM Suite: automated enrichment of Security Coverage

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

![Active OpenAEV Coverage connector in OpenCTI](assets/active_openaev_connector_in_opencti.png)

### Trigger security coverage enrichments from OpenCTI

Once the connector appears in OpenCTI, you can trigger it to run security coverage enrichments. Refer to the [OpenCTI documentation](https://docs.opencti.io/latest/) for how to trigger the enabled connector to get automated enriched security posture assessments with OpenAEV.

## Example workflow

1. A threat analyst identifies a new intrusion set in **OpenCTI**.
2. The analyst triggers the **OpenAEV Coverage** connector on the associated Security Coverage object.
3. OpenAEV receives the request, maps it to an existing scenario, and executes the simulation.
4. Results (detection rate, prevention rate, findings) are pushed back to OpenCTI as enrichment data.
5. The analyst sees the updated security posture directly in the OpenCTI interface.

## What's next?

- [Scenario Generation from OpenCTI Security Coverage](scenario/security-coverage.md) — Automatically create OpenAEV scenarios from OpenCTI Security Coverage objects.
- [Configuration reference](../deployment/configuration.md#xtm-suite-opencti) — Full list of configuration parameters.
- [Scenarios and Simulations](scenarios-and-simulations.md) — Understand how scenarios and simulations work in OpenAEV.
