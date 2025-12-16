package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.integration.ManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class ManagerIntegrationsSyncJob implements Job {

  private final EngineService engineService;
  private final EngineContext engineContext;
  private final ManagerFactory managerFactory;

  @Override
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    managerFactory.getManager().run();
  }
}
