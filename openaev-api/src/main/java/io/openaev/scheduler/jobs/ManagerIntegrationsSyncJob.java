package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
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
  private static final long EXECUTION_TIME_THRESHOLD = 500;

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
          log.error("Failed to sync integrations for tenant '{}': {}", tenantId, e.getMessage(), e);
        } finally {
          TenantContext.clearCurrentTenant();
          long tenantDuration = System.currentTimeMillis() - tenantStart;
          if (tenantDuration > EXECUTION_TIME_THRESHOLD) {
            log.warn(
                "==> managerFactory.getManager(tenantId).monitorIntegrations() for tenant '{}' took {} ms (>500ms threshold)",
                tenantId,
                tenantDuration);
          }
        }
      }
      long jobDuration = System.currentTimeMillis() - jobStart;
      if (jobDuration > EXECUTION_TIME_THRESHOLD) {
        log.warn(
            "==> ManagerIntegrationsSyncJob.execute took {} ms (>500ms threshold)", jobDuration);
      }
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
