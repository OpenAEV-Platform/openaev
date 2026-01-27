package io.openaev.scheduler;

import static org.quartz.JobKey.jobKey;

import io.openaev.scheduler.jobs.*;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class PlatformJobDefinitions {

  @Bean
  public JobDetail getInjectsExecution() {
    return JobBuilder.newJob(InjectsExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("InjectsExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getComchecksExecution() {
    return JobBuilder.newJob(ComchecksExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("ComchecksExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getScenarioExecution() {
    return JobBuilder.newJob(ScenarioExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("ScenarioExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getEngineSyncExecution() {
    return JobBuilder.newJob(EngineSyncExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("EngineSyncExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail managerIntegrationsSync() {
    return JobBuilder.newJob(ManagerIntegrationsSyncJob.class)
        .storeDurably()
        .withIdentity(jobKey("managerIntegrationsSync"))
        .build();
  }

  @Bean
  public JobDetail getSecurityCoverageJobExecution() {
    return JobBuilder.newJob(SecurityCoverageJob.class)
        .storeDurably()
        .withIdentity(jobKey("SecurityCoverageJob"))
        .build();
  }

  @Bean
  public JobDetail getConnectorPingJob() {
    return JobBuilder.newJob(OpenCTIConnectorRegisterPingJob.class)
        .storeDurably()
        .withIdentity(jobKey("ConnectorPingJob"))
        .build();
  }
}
