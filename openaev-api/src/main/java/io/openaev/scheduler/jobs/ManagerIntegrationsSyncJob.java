package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class ManagerIntegrationsSyncJob implements Job {
  private final ManagerFactory managerFactory;
  private final TenantService tenantService;
  private static final long EXECUTION_TIME_THRESHOLD = 500;
  private static final long TENANT_EXECUTION_TIME_THRESHOLD = 250;
  private final @Qualifier("managerIntegrationsExecutor") Executor managerIntegrationsExecutor;
  // Track in-flight tenant syncs to avoid scheduling overlapping runs.
  private final Set<String> runningTenantSyncs = ConcurrentHashMap.newKeySet();

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    long jobStart = System.currentTimeMillis();
    try {
      List<String> tenantIds = tenantService.findActiveTenantIds();
      for (String tenantId : tenantIds) {
        if (!runningTenantSyncs.add(tenantId)) {
          log.warn(
              String.format(
                  "Skipping integration sync for tenant '%s' because a previous run is still in progress",
                  tenantId));
          continue;
        }
        try {
          CompletableFuture.runAsync(
              () -> {
                try {
                  monitorTenantIntegrations(tenantId);
                } finally {
                  runningTenantSyncs.remove(tenantId);
                }
              },
              managerIntegrationsExecutor);
        } catch (RuntimeException e) {
          runningTenantSyncs.remove(tenantId);
          throw new JobExecutionException(e);
        }
      }
      long jobDuration = System.currentTimeMillis() - jobStart;
      if (jobDuration > EXECUTION_TIME_THRESHOLD) {
        log.warn(
            String.format(
                "==> ManagerIntegrationsSyncJob.execute took %d ms (>500ms threshold)",
                jobDuration));
      }
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }

  private void monitorTenantIntegrations(String tenantId) {
    long tenantStart = System.currentTimeMillis();
    try {
      TenantContext.setCurrentTenant(tenantId);
      managerFactory.getManager(tenantId).monitorIntegrations();
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to sync integrations for tenant '%s': %s", tenantId, e.getMessage()),
          e);
    } finally {
      TenantContext.clearCurrentTenant();
      long tenantDuration = System.currentTimeMillis() - tenantStart;
      if (tenantDuration > EXECUTION_TIME_THRESHOLD) {
        log.warn(
            String.format(
                "==> managerFactory.getManager(tenantId).monitorIntegrations() for tenant '%s' took %d ms (threshold %d ms)",
                tenantId, tenantDuration, TENANT_EXECUTION_TIME_THRESHOLD));
      }
    }
  }
}
