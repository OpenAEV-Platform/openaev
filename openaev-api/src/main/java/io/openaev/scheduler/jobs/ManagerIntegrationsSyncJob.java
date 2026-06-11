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

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    try {
      List<String> tenantIds = tenantService.findActiveTenantIds();
      log.info(
          "===> ManagerIntegrationsSyncJob: starting sync for {} tenant(s): {}",
          tenantIds.size(),
          tenantIds);
      for (String tenantId : tenantIds) {
        try {
          TenantContext.setCurrentTenant(tenantId);
          managerFactory.getManager(tenantId).monitorIntegrations();
        } catch (Exception e) {
          log.error("Failed to sync integrations for tenant '{}': {}", tenantId, e.getMessage(), e);
        } finally {
          TenantContext.clearCurrentTenant();
        }
      }
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
