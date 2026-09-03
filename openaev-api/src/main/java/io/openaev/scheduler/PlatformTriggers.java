package io.openaev.scheduler;

import static io.openaev.scheduler.jobs.CredentialConnectivityCheckJob.CREDENTIAL_CONNECTIVITY_CHECK_TRIGGER;
import static io.openaev.scheduler.jobs.EngineDeletionReplayJob.ENGINE_DELETION_REPLAY_TRIGGER;
import static io.openaev.scheduler.jobs.ExecutionTraceRetentionJob.EXECUTION_TRACE_RETENTION_TRIGGER;
import static io.openaev.scheduler.jobs.TenantPurgeJob.TENANT_PURGE_TRIGGER;
import static io.openaev.scheduler.jobs.UrlAccessTokenPurgeJob.URL_ACCESS_TOKEN_PURGE_TRIGGER;
import static io.openaev.scheduler.jobs.notification.NotificationDigestJob.NOTIFICATION_DIGEST_TRIGGER;
import static io.openaev.scheduler.jobs.notification.NotificationEventRetentionJob.NOTIFICATION_EVENT_RETENTION_TRIGGER;
import static io.openaev.scheduler.jobs.reporting.ReportingScheduleJob.REPORTING_SCHEDULE_TRIGGER;
import static io.openaev.scheduler.jobs.user_event.UserEventRetentionJob.USER_EVENT_RETENTION_TRIGGER;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.*;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
// Safe mode keeps web/UI available by not registering Quartz triggers at startup.
@ConditionalOnProperty(name = "openaev.run-mode", havingValue = "normal", matchIfMissing = true)
public class PlatformTriggers {

  private PlatformJobDefinitions platformJobs;

  @Value("${openaev.cron.config.steps.delay.queue.polling.interval:10000}")
  private int stepDelayQueue;

  @Value("${openaev.credentials.status-validation.cron:0 */6 * * * ?}")
  private String credentialsConnectivityCheckCron;

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
  public Trigger injectsFinalizationTrigger() {
    return newTrigger()
        .forJob(platformJobs.getInjectsFinalization())
        .withIdentity("InjectsFinalizationTrigger")
        // Offset from the dispatch job so the two never contend for the same DB connections
        .withSchedule(cronSchedule("30 0/1 * * * ?"))
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
  @Profile("!test")
  public Trigger scenarioExecutionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getScenarioExecution())
        .withIdentity("ScenarioExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger atomicTestingExecutionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getAtomicTestingExecution())
        .withIdentity("AtomicTestingExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger managerIntegrationsSyncTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.managerIntegrationsSync())
        .withIdentity("managerIntegrationsSync")
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

  @Bean
  @Profile("!test")
  public Trigger connectorPingTrigger() {
    // 40 seconds is recommended for OCTI connectors pings
    SimpleScheduleBuilder _40_seconds = simpleSchedule().withIntervalInSeconds(40).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.getConnectorPingJob())
        .withIdentity("connectorPingJob")
        .withSchedule(_40_seconds)
        .build();
  }

  @Bean
  public Trigger userEventRetentionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.userEventRetentionJobDetail())
        .withIdentity(USER_EVENT_RETENTION_TRIGGER)
        .withSchedule(cronSchedule("0 0 0 * * ?"))
        .build();
  }

  /**
   * Create a trigger to run the requeue system for the execution traces
   *
   * @return the trigger
   */
  @Bean
  public Trigger executionTracesBatchRequeueTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getExecutionTracesBatchRequeueJob())
        .withIdentity("ExecutionTracesBatchRequeueTrigger")
        .withSchedule(repeatSecondlyForever(15))
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger queueChainingTrigger() {
    SimpleScheduleBuilder _10_seconds =
        simpleSchedule().withIntervalInMilliseconds(stepDelayQueue).repeatForever();

    return newTrigger()
        .forJob(this.platformJobs.queueChainingJobDetail())
        .withIdentity("QueueChainingJob")
        .withSchedule(_10_seconds)
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger workflowTimeoutTrigger() {
    SimpleScheduleBuilder every30Seconds =
        simpleSchedule().withIntervalInSeconds(30).repeatForever();

    return newTrigger()
        .forJob(this.platformJobs.workflowTimeoutJobDetail())
        .withIdentity("WorkflowTimeoutJob")
        .withSchedule(every30Seconds)
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger autonomousTimeoutTrigger() {
    SimpleScheduleBuilder every30Seconds =
        simpleSchedule().withIntervalInSeconds(30).repeatForever();

    return newTrigger()
        .forJob(this.platformJobs.autonomousTimeoutJobDetail())
        .withIdentity("AutonomousTimeoutJob")
        .withSchedule(every30Seconds)
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger executionTraceRetentionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.executionTraceRetentionJobDetail())
        .withIdentity(EXECUTION_TRACE_RETENTION_TRIGGER)
        .withSchedule(cronSchedule("0 30 1 * * ?")) // Daily at 1:30 AM
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger tenantPurgeTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.tenantPurgeJobDetail())
        .withIdentity(TENANT_PURGE_TRIGGER)
        .withSchedule(cronSchedule("0 0 2 * * ?")) // Daily at 2:00 AM
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger notificationDigestTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.notificationDigestJobDetail())
        .withIdentity(NOTIFICATION_DIGEST_TRIGGER)
        .withSchedule(cronSchedule("0 0/1 * * * ?")) // Every minute align on clock
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger reportingScheduleTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.reportingScheduleJobDetail())
        .withIdentity(REPORTING_SCHEDULE_TRIGGER)
        .withSchedule(cronSchedule("0 0/1 * * * ?")) // Every minute align on clock
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger notificationEventRetentionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.notificationEventRetentionJobDetail())
        .withIdentity(NOTIFICATION_EVENT_RETENTION_TRIGGER)
        .withSchedule(cronSchedule("0 15 1 * * ?")) // Daily at 1:15 AM
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger urlAccessTokenPurgeTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.urlAccessTokenPurgeJobDetail())
        .withIdentity(URL_ACCESS_TOKEN_PURGE_TRIGGER)
        .withSchedule(cronSchedule("0 0 2 ? * SUN")) // Every Sunday at 2:00 AM
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger credentialsStatusValidatorTrigger() {
    // Off-peak by default: a run makes one outbound call per stale credential, and the providers
    // it talks to (Azure AD, ARM) are the same ones simulations depend on during the day.
    return newTrigger()
        .forJob(this.platformJobs.credentialsConnectivityCheckJobDetail())
        .withIdentity(CREDENTIAL_CONNECTIVITY_CHECK_TRIGGER)
        .withSchedule(cronSchedule(credentialsConnectivityCheckCron))
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger engineDeletionReplayTrigger() {
    // Replays journaled deletions against the search engine: must run frequently enough that a
    // document resurrected by an in-flight indexer batch disappears quickly from dashboards.
    SimpleScheduleBuilder every60Seconds =
        simpleSchedule().withIntervalInSeconds(60).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.engineDeletionReplayJobDetail())
        .withIdentity(ENGINE_DELETION_REPLAY_TRIGGER)
        .withSchedule(every60Seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }
}
