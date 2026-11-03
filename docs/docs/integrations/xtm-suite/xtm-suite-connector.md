# Security Coverage enrichment (XTM Suite)

This page explains how to connect OpenAEV to OpenCTI, so that OpenCTI can trigger Scenarios in OpenAEV and receive their results, such as detection and prevention rates.

## What is this?

The XTM Suite connector lets OpenAEV exchange data with other Filigran products. OpenAEV sends its validation results (detection rate, prevention rate) back to the connected product. Today, this works with OpenCTI.

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

#### Step 1: configure OpenAEV to connect to OpenCTI

Each OpenCTI connection belongs to an OpenAEV Tenant, identified by its UUID (`{id}`). Each Tenant in a [multi-tenant deployment](../../administration/multi-tenancy.md) can have its own OpenCTI connection.

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

    The `{id}` is the Tenant UUID (for example `2cffad3a-0001-4078-b0e2-ef74274022c3`), shown as **Tenant identifier** in **Settings > Parameters**.

!!! tip "API URL override"

    Set `openaev.xtm.opencti.{id}.api-url` only if your GraphQL endpoint is not `<url>/graphql`, for example behind a reverse proxy with a custom path.

#### Step 2: restart OpenAEV

After updating the configuration, restart the OpenAEV platform for the changes to take effect.

#### Step 3: check the connection

In OpenCTI, open **Data > Ingestion > Monitoring**: the **OpenAEV Coverage** connector is listed.

![Active OpenAEV Coverage connector in OpenCTI](assets/active_openaev_connector_in_opencti.png)

### Trigger enrichments from OpenCTI

Once the connector appears in OpenCTI, you can trigger it to run security coverage enrichments. Refer to the [OpenCTI Security Coverage documentation](https://docs.opencti.io/latest/usage/security-coverage/) for how to trigger the enabled connector to get automated enriched security posture assessments with OpenAEV.

## Example workflow

1. A threat analyst identifies a new intrusion set in **OpenCTI**.
2. The analyst triggers the **OpenAEV Coverage** connector on the associated Security Coverage object.
3. OpenAEV receives the request, maps it to an existing scenario, and executes the simulation.
4. Results (detection rate, prevention rate, findings) are pushed back to OpenCTI as enrichment data.
5. The analyst sees the updated security posture directly in the OpenCTI interface.

## What's next?

- [XTM One](xtm-one.md) -- Connect OpenAEV to XTM One for its AI features
- [Scenario generation from OpenCTI security coverage](../../usage/build/scenario/security-coverage.md) -- Create Scenarios from OpenCTI Security Coverage objects
- [Configuration reference](../../reference/deployment/configuration.md#xtm-suite-opencti) -- All configuration parameters
- [Scenarios and Simulations](../../usage/get-started/foundations/scenarios-and-simulations.md) -- How Scenarios and Simulations work
