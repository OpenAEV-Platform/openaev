package io.openaev.scheduler.jobs;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.service.asset.AssetFreshnessService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Raises the STALE transitions of the assets, one tenant at a time, each in its own tenant-scoped
 * transaction ({@link TenantScopedTransaction#forEachTenant}): an asset whose last observation
 * crossed the tenant's threshold since the previous pass gets one asset update event (see {@link
 * AssetFreshnessService}). The previous pass is kept per tenant in memory; the first pass after a
 * start looks back one interval. The status itself is read from the stamp, so a missed pass loses
 * an event, never a verdict.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class AssetFreshnessJob implements Job {

  public static final String ASSET_FRESHNESS_JOB = "AssetFreshnessJob";
  public static final String ASSET_FRESHNESS_TRIGGER = "AssetFreshnessTrigger";

  private final AssetFreshnessService assetFreshnessService;
  private final TenantScopedTransaction tenantTx;

  @Value("${openaev.cron.config.asset.freshness.interval:60}")
  private int intervalMinutes = 60;

  private final Map<String, Instant> previousPassByTenant = new ConcurrentHashMap<>();

  @Override
  public void execute(JobExecutionContext context) {
    Instant now = Instant.now();
    Instant firstLookBack = now.minus(Duration.ofMinutes(Math.max(1, intervalMinutes)));
    tenantTx.forEachTenant(
        tenantId -> {
          Instant previous = previousPassByTenant.getOrDefault(tenantId, firstLookBack);
          assetFreshnessService.raiseStaleTransitions(tenantId, previous, now);
          previousPassByTenant.put(tenantId, now);
        });
  }
}
