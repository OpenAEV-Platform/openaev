# Usage telemetry

The application collects statistical data related to its usage and performances.

!!! note "Confidentiality"

    The OpenBAS platform does not collect any information related to vulnerability which remains strictly confidential. Also, the collection is strictly anonymous and personally identifiable information is NOT collected (including IP addresses).

We do not collect any personal data, only statistical data. All collected data is aggregated to ensure privacy and compliance with relevant privacy regulations (see breakdown below for details).

## Purpose of the telemetry

The collected data is used for the following purposes:

- Improving the functionality and performance of the application.
- Analyzing user behavior to enhance user experience.
- Generating aggregated and anonymized statistics for internal and external reporting.

## Important thing to know

The platform send the metrics to the hostname `telemetry.filigran.io` using the OTLP protocol (over HTTPS). The format of the data is OpenTelemetry JSON.

The metrics push is done every 6 hours if OpenBAS was able to connect to the hostname when the telemetry manager is started.

## Telemetry metrics

The application collects statistical data related to its usage. Here is an exhaustive list of the collected metrics:

- The current platform version
- The platform's unique identifier
- The platform creation date
- Enterprise Edition status (activated or not)
- The total number of users
- The total number of agents deployed
- The total number of agents deployed as services or sessions
- The total number of agents deployed for users or admins
- The total number of agents deployed for each executor (e.g. Caldera, OpenBAS, CrowdStrike, etc.)
- The number of simulations, scenarios, and atomic tests created
- The number of simulations or injects executed

