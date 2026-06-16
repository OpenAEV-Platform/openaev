package io.openaev.telemetry.metric_collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Records operational metrics for {@code ManagerIntegrationsSyncJob}: job duration, per-tenant
 * duration, tenant count, and error count. Exported via the existing OpenTelemetry pipeline.
 */
@Component
public class ManagerSyncMetrics {

  private static final AttributeKey<String> TENANT_ID_KEY = AttributeKey.stringKey("tenant_id");

  private final DoubleHistogram jobDuration;
  private final DoubleHistogram tenantDuration;
  private final LongCounter tenantsProcessed;
  private final LongCounter syncErrors;

  public ManagerSyncMetrics(@Lazy Meter meter) {
    this.jobDuration =
        meter
            .histogramBuilder("manager_sync_job_duration_ms")
            .setDescription("Total duration of ManagerIntegrationsSyncJob.execute in milliseconds")
            .setUnit("ms")
            .build();

    this.tenantDuration =
        meter
            .histogramBuilder("manager_sync_tenant_duration_ms")
            .setDescription("Duration of monitorIntegrations per tenant in milliseconds")
            .setUnit("ms")
            .build();

    this.tenantsProcessed =
        meter
            .counterBuilder("manager_sync_tenants_processed_total")
            .setDescription("Number of tenants processed per sync run")
            .setUnit("count")
            .build();

    this.syncErrors =
        meter
            .counterBuilder("manager_sync_errors_total")
            .setDescription("Number of tenant sync failures")
            .setUnit("count")
            .build();
  }

  /** Record total job execution duration. */
  public void recordJobDuration(long durationMs, int tenantCount) {
    jobDuration.record(durationMs);
    tenantsProcessed.add(tenantCount);
  }

  /** Record per-tenant monitorIntegrations duration. */
  public void recordTenantDuration(String tenantId, long durationMs) {
    tenantDuration.record(durationMs, Attributes.of(TENANT_ID_KEY, tenantId));
  }

  /** Record a sync error for a tenant. */
  public void recordError(String tenantId) {
    syncErrors.add(1, Attributes.of(TENANT_ID_KEY, tenantId));
  }
}
