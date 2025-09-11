package io.openbas.scheduler;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.repeatMinutelyForever;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
public class PlatformTriggers {

  private PlatformJobDefinitions platformJobs;

  @Autowired
  public void setPlatformJobs(PlatformJobDefinitions platformJobs) {
    this.platformJobs = platformJobs;
  }

  @Bean
  public Trigger injectsExecutionTrigger() {
    return newTrigger()
        .forJob(platformJobs.getInjectsExecution())
        .withIdentity("InjectsExecutionTrigger")
        .withSchedule(cronSchedule("0 0/1 * * * ?")) // Every minute align on clock
        .build();
  }

  @Bean
  public Trigger comchecksExecutionTrigger() {
    return newTrigger()
        .forJob(platformJobs.getComchecksExecution())
        .withIdentity("ComchecksExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  public Trigger scenarioExecutionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getScenarioExecution())
        .withIdentity("ScenarioExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger elasticSyncExecutionTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.getEngineSyncExecution())
        .withIdentity("engineSyncExecutionTrigger")
        .withSchedule(_15_seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger securityCoverageTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.getSecurityCoverageJobExecution())
        .withIdentity("securityCoverageTrigger")
        .withSchedule(_15_seconds)
        .build();
  }
}
