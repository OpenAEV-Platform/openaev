package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
import io.openaev.telemetry.metric_collectors.ManagerSyncMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class ManagerIntegrationsSyncJob implements Job {
  private final ManagerFactory managerFactory;
  private final TenantService tenantService;
  private final ManagerSyncMetrics managerSyncMetrics;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    long jobStart = System.currentTimeMillis();
    try {
      List<String> tenantIds = tenantService.findActiveTenantIds();
      for (String tenantId : tenantIds) {
        long tenantStart = System.currentTimeMillis();
        try {
          TenantContext.setCurrentTenant(tenantId);
          managerFactory.getManager(tenantId).monitorIntegrations();
        } catch (Exception e) {
          managerSyncMetrics.recordError(tenantId);
          log.error("Failed to sync integrations for tenant '{}': {}", tenantId, e.getMessage(), e);
        } finally {
          TenantContext.clearCurrentTenant();
          long tenantDuration = System.currentTimeMillis() - tenantStart;
          managerSyncMetrics.recordTenantDuration(tenantId, tenantDuration);
          if (tenantDuration > 500) {
            log.warn(
                "==> ManagerIntegrationsSyncJob.executegst for tenant '{}' took {} ms (>500ms threshold)",
                tenantId,
                tenantDuration);
          }
        }
      }
      long jobDuration = System.currentTimeMillis() - jobStart;
      managerSyncMetrics.recordJobDuration(jobDuration, tenantIds.size());
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
