package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.integration.ManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class ManagerIntegrationsSyncJob implements Job {
  private final ManagerFactory managerFactory;

  @Lazy @Autowired private ManagerIntegrationsSyncJob proxySelf;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    TxCtx ctx = TxCtx.noTenant();
    proxySelf.txExecute(ctx);
  }

  @Transactional(rollbackFor = Exception.class)
  public void txExecute(@SuppressWarnings("unused") TxCtx _ctx) throws JobExecutionException {
    try {
      managerFactory.getManager().monitorIntegrations();
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
