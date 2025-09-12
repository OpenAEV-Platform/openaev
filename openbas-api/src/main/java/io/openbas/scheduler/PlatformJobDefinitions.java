package io.openbas.scheduler;

import static org.quartz.JobKey.jobKey;

import io.openbas.scheduler.jobs.*;
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
        .withIdentity(jobKey("ElasticSyncExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getSecurityCoverageJobExecution() {
    return JobBuilder.newJob(SecurityCoverageJob.class)
        .storeDurably()
        .withIdentity(jobKey("SecurityCoverageJob"))
        .build();
  }
}
