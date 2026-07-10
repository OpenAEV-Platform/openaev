# Usage telemetry

The application collects statistical data related to its usage and performances.

!!! note "Confidentiality"

    The OpenAEV platform does not collect any information related to vulnerability which remains strictly confidential. Also, the collection is strictly anonymous and personally identifiable information is NOT collected (including IP addresses).

We do not collect any personal data, only statistical data. All collected data is aggregated to ensure privacy and compliance with relevant privacy regulations (see breakdown below for details).

## Purpose of the telemetry

The collected data is used for the following purposes:

- Improving the functionality and performance of the application.
- Analyzing user behavior to enhance user experience.
- Generating aggregated and anonymized statistics for internal and external reporting.

## Important thing to know

The platform send the metrics to the hostname `telemetry.filigran.io` using the OTLP protocol (over HTTPS). The format of the data is OpenTelemetry JSON.

The metrics push is done every 6 hours if OpenAEV was able to connect to the hostname when the telemetry manager is started.

## Telemetry metrics

The application collects statistical data related to its usage. Here is an exhaustive list of the collected metrics:

- The current platform version
- The platform's unique identifier
- The platform creation date
- The deployment tags, when configured by the operator (`telemetry.tags` / `TELEMETRY_TAGS` - comma-separated freeform tags such as `saas,eu-west`, normalized to lowercase and sorted before export)
- Enterprise Edition status (activated or not)
- The total number of users
- The number of assets, broken down by asset category (host, cloud resource, web application, ...) and by agent coverage (agent based when at least one agent is installed on the asset, agentless otherwise). Counts only, no asset content.
- The deployed injectors, collectors, and executors broken down by catalog identity (the catalog connector slug for components deployed from the catalog, otherwise the component's own type identifier, with a managed/manual flag). No component configuration is ever collected.
- The number of simulations, scenarios, and atomic tests created
- The number of simulations or injects executed
- The number of injects played, broken down by injector type
- AI usage counters, without any content: AI feature calls by feature (fix spelling, summarize, generate message, etc. - identical whichever backend serves them), chatbot messages, generic AI agent calls by agent identifier, TTP extractions, AI detection/remediation rule generations by collector type, and inject assistant runs
- AI and ecosystem configuration state: whether the built-in LLM is enabled (with the provider type), whether XTM One is configured, whether the chatbot AI terms of use have been accepted, and whether the platform is registered on XTM Hub
- Authentication strategies enabled (local, OIDC, SAML2, Kerberos) - configuration booleans only
- Content inventory counts, without any content: payloads (by type, source and status), teams, endpoints (by platform), asset groups, security platforms, organizations, injects, challenges, documents, channels, articles, custom dashboards, import mappers, notification rules, workflows, findings, vulnerabilities, CVEs, vulnerable endpoints, attack patterns, reports, and recurring scenarios
- Results and automation counters: expectation validation traces by collector type, security coverage bundles processed / scenarios generated / results sent, workflow runs and timeout-forced completions, payloads created / duplicated / upserted, and emails sent

