package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    long jobStart = System.currentTimeMillis();
    try {
      List<String> tenantIds = tenantService.findActiveTenantIds();
      for (String tenantId : tenantIds) {
        CompletableFuture.runAsync(
            () -> monitorTenantIntegrations(tenantId), managerIntegrationsExecutor);
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
