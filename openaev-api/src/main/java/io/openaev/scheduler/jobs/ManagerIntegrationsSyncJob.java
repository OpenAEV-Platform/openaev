package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.integration.ManagerFactory;
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

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    try {
      List<String> tenantIds = managerFactory.getTenantIds();
      log.info("===> ManagerIntegrationsSyncJob: starting sync for {} tenant(s): {}", tenantIds.size(), tenantIds);
      managerFactory.monitorAllTenants();
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
