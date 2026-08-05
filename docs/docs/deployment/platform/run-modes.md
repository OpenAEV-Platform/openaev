# Run modes

OpenAEV supports two startup run modes controlled by `openaev.run-mode`.

## Modes

- **normal**: all features are enabled and running.
- **safe**: background processing is disabled.

## Safe mode behavior

When `openaev.run-mode=safe`, Quartz background jobs are not started at boot.  
The web application remains available and a banner is shown in the frontend to indicate degraded operation.

## Impacted features in safe mode

The following feature areas are impacted because their Quartz jobs are disabled:

| Feature area | Quartz jobs disabled in safe mode |
|:--|:--|
| Inject/Comcheck/Scenario/Atomic execution | `InjectsExecutionJob`, `ComchecksExecutionJob`, `ScenarioExecutionJob`, `AtomicTestingExecutionJob` |
| Integrations and security coverage refresh | `ManagerIntegrationsSyncJob`, `SecurityCoverageJob`, `OpenCTIConnectorRegisterPingJob` |
| Chaining workflow processing | `QueueChainingJob`, `WorkflowTimeoutJob` |
| Reporting and notifications | `ReportingScheduleJob`, `NotificationDigestJob`, `NotificationEventRetentionJob` |
| Data retention and purge | `ExecutionTraceRetentionJob`, `ExecutionTracesBatchRequeueJob`, `UserEventRetentionJob`, `TenantPurgeJob`, `UrlAccessTokenPurgeJob` |
| Search engine maintenance and sync | `EngineDeletionReplayJob`, `EngineSyncExecutionJob` |

## Configuration example

```properties
openaev.run-mode=safe
```

## Not to be confused with debug mode

Debug mode (`openaev.debug.*`) is a separate diagnostics feature and is documented in
[Global debug mode](../../administration/debug-mode.md). It is independent from platform run modes.
