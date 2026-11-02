# Usage telemetry

OpenAEV sends usage statistics to Filigran. This page lists what is sent, where and how often, and
how to stop it.

!!! note "What telemetry contains"

    Telemetry sends counts, configuration flags and a few labels. The labels are:

    - the IP address of the server running the platform;
    - the platform unique identifier;
    - Security Platform names, as entered by users;
    - AI agent identifiers (lowercased, at most 64 characters);
    - the deployment tags;
    - the workflow safety policy values.

    No end-user or client IP address is sent, and no content of your Scenarios, Findings,
    vulnerabilities or Payloads.

## Purpose of the telemetry

Filigran uses the data to:

- improve the features and performance of the platform;
- understand how the platform is used, to improve the user experience;
- produce aggregated statistics for internal and external reporting.

## How data is sent

- **Destination**: `https://telemetry.oaev.filigran.io/v1/metrics`, using OTLP (OpenTelemetry
  Protocol) over HTTPS, encoded as protobuf.
- **Frequency**: metrics are collected every 60 minutes. The first export happens about 5 hours
  after the platform starts, then every 6 hours.
- **Startup check**: when the platform starts, it tests the connection to the destination. If the
  destination cannot be reached, telemetry stays off until the next restart.

## How to disable telemetry

There is no setting to turn telemetry off. To stop it, block outbound access or DNS resolution to
`telemetry.oaev.filigran.io` from the platform, then restart the platform. Blocking without a
restart only makes the exports fail. Air-gapped platforms never send telemetry.

## Telemetry metrics

Metrics are counts and flags, grouped by area:

- **Platform identity**: version, unique identifier, creation date, server IP address and deployment tags (`telemetry.tags`).
- **Usage**: Enterprise Edition status, users and logins, Tenants, Assets, deployed integrations, Scenarios, Simulations and Injects played.
- **AI and ecosystem**: AI feature usage (no content), AI and XTM One configuration, XTM Hub registration, enabled authentication methods.
- **Content inventory**: number of Payloads, Teams, Assets, Findings, vulnerabilities, Dashboards and other objects.
- **Credentials**: number of credentials by type, authentication method and connectivity status, and number of Injects using a credential. No credential name or secret is sent.
- **Results and automation**: expectation traces, security coverage processing, workflow runs, Payload creation and emails sent.

## What's next?

- [Platform managers](../../deployment/platform/managers.md) -- How the telemetry manager runs
- [Configuration](configuration.md) -- All platform configuration parameters
