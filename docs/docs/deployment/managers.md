# Platform managers

Platform managers are background services that perform recurring tasks to support core platform functionality. They run automatically and require no manual intervention.

## Filigran telemetry manager

The telemetry manager collects anonymous usage statistics about the platform at regular intervals. Data is aggregated every 60 minutes and exported every 6 hours to the Filigran telemetry endpoint.

If the telemetry endpoint is unreachable (e.g., in air-gapped environments), the manager gracefully degrades and stops collection without affecting platform operations.

You can check whether telemetry is active from **Settings > Parameters** in the Tools panel.

More information about collected data can be found in the [Telemetry reference](../reference/deployment/telemetry.md).

## Integration managers

Integration managers handle the lifecycle of external connectors such as email services, Caldera, CrowdStrike, and other Injectors and Collectors. Each Tenant gets its own integration manager instance, ensuring isolation between workspaces.

Integration managers are responsible for:

- Starting and stopping connector instances
- Monitoring connector health
- Synchronizing connector state with the platform

## Scheduled jobs

The platform runs several recurring background jobs:

| Job | Description |
|---|---|
| Scenario recurrence | Launches scheduled Simulations based on Scenario cron expressions |
| Expectation expiration | Marks timed-out expectations as expired |
| Notification dispatch | Sends email alerts for score degradation and other triggers |
| Integration sync | Synchronizes connector registrations across Tenants |

## What's next?

- [Telemetry reference](../reference/deployment/telemetry.md) -- Details on collected telemetry data
- [Configuration](configuration.md) -- Platform configuration properties
