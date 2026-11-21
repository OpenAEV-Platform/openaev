package io.openaev.scheduler.jobs;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.service.asset.AssetFreshnessService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Turns unseen assets stale and seen-again assets fresh, one tenant at a time, each in its own
 * tenant-scoped transaction ({@link TenantScopedTransaction#forEachTenant}). Every transition is an
 * asset update event (see {@link AssetFreshnessService}).
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

  @Override
  public void execute(JobExecutionContext context) {
    Instant now = Instant.now();
    tenantTx.forEachTenant(tenantId -> assetFreshnessService.refreshTenant(tenantId, now));
  }
}
