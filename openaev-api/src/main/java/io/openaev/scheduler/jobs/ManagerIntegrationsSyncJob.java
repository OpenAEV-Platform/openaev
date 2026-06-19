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
              "Skipping integration sync for tenant '{}' because a previous run is still in progress",
              tenantId);
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
        log.error(
            "!!!!!!!!!!!!!!!!!!!!!!!!!! ManagerIntegrationsSyncJob dispatch took {} ms (threshold {} ms)",
            jobDuration,
            EXECUTION_TIME_THRESHOLD);
      }
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }

  private void monitorTenantIntegrations(String tenantId) {
    long tenantStart = System.currentTimeMillis();
    try {
      // TODO check thread local -> seems OK but ?
      TenantContext.setCurrentTenant(tenantId);
      managerFactory.getManager(tenantId).monitorIntegrations();
    } catch (Exception e) {
      log.error("Failed to sync integrations for tenant '{}': {}", tenantId, e.getMessage(), e);
    } finally {
      TenantContext.clearCurrentTenant();
      long tenantDuration = System.currentTimeMillis() - tenantStart;
      if (tenantDuration > TENANT_EXECUTION_TIME_THRESHOLD) {
        log.error(
            "!!!!!!!!!!!!!!!!!!!!!!!!!!  Integration sync for tenant '{}' took {} ms (threshold {} ms)",
            tenantId,
            tenantDuration,
            TENANT_EXECUTION_TIME_THRESHOLD);
      }
    }
  }
}
