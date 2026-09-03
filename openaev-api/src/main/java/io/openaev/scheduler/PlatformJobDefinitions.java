package io.openaev.scheduler;

import static io.openaev.scheduler.jobs.CredentialConnectivityCheckJob.CREDENTIAL_CONNECTIVITY_CHECK_JOB;
import static io.openaev.scheduler.jobs.EngineDeletionReplayJob.ENGINE_DELETION_REPLAY_JOB;
import static io.openaev.scheduler.jobs.ExecutionTraceRetentionJob.EXECUTION_TRACE_RETENTION_JOB;
import static io.openaev.scheduler.jobs.TenantPurgeJob.TENANT_PURGE_JOB;
import static io.openaev.scheduler.jobs.UrlAccessTokenPurgeJob.URL_ACCESS_TOKEN_PURGE_JOB;
import static io.openaev.scheduler.jobs.notification.NotificationDigestJob.NOTIFICATION_DIGEST_JOB;
import static io.openaev.scheduler.jobs.notification.NotificationEventRetentionJob.NOTIFICATION_EVENT_RETENTION_JOB;
import static io.openaev.scheduler.jobs.reporting.ReportingGenerationReaperJob.REPORTING_GENERATION_REAPER_JOB;
import static io.openaev.scheduler.jobs.reporting.ReportingScheduleJob.REPORTING_SCHEDULE_JOB;
import static io.openaev.scheduler.jobs.user_event.UserEventRetentionJob.USER_EVENT_RETENTION_JOB;
import static org.quartz.JobKey.jobKey;

import io.openaev.scheduler.jobs.*;
import io.openaev.scheduler.jobs.notification.NotificationDigestJob;
import io.openaev.scheduler.jobs.notification.NotificationEventRetentionJob;
import io.openaev.scheduler.jobs.reporting.ReportingGenerationReaperJob;
import io.openaev.scheduler.jobs.reporting.ReportingScheduleJob;
import io.openaev.scheduler.jobs.user_event.UserEventRetentionJob;
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
  public JobDetail getInjectsFinalization() {
    return JobBuilder.newJob(InjectsFinalizationJob.class)
        .storeDurably()
        .withIdentity(jobKey("InjectsFinalizationJob"))
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
  public JobDetail getAtomicTestingExecution() {
    return JobBuilder.newJob(AtomicTestingExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("AtomicTestingExecutionJob"))
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

  @Bean
  public JobDetail userEventRetentionJobDetail() {
    return JobBuilder.newJob(UserEventRetentionJob.class)
        .withIdentity(USER_EVENT_RETENTION_JOB)
        .storeDurably()
        .build();
  }

  /**
   * Create the job for the requeue system of the execution traces
   *
   * @return the job
   */
  @Bean
  public JobDetail getExecutionTracesBatchRequeueJob() {
    return JobBuilder.newJob(ExecutionTracesBatchRequeueJob.class)
        .withIdentity("executionTracesBatchRequeueJob")
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail executionTraceRetentionJobDetail() {
    return JobBuilder.newJob(ExecutionTraceRetentionJob.class)
        .withIdentity(EXECUTION_TRACE_RETENTION_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail tenantPurgeJobDetail() {
    return JobBuilder.newJob(TenantPurgeJob.class)
        .withIdentity(TENANT_PURGE_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail queueChainingJobDetail() {
    return JobBuilder.newJob(QueueChainingJob.class)
        .withIdentity("QueueChainingJob")
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail workflowTimeoutJobDetail() {
    return JobBuilder.newJob(WorkflowTimeoutJob.class)
        .withIdentity("WorkflowTimeoutJob")
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail autonomousTimeoutJobDetail() {
    return JobBuilder.newJob(AutonomousTimeoutJob.class)
        .withIdentity("AutonomousTimeoutJob")
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail notificationDigestJobDetail() {
    return JobBuilder.newJob(NotificationDigestJob.class)
        .withIdentity(NOTIFICATION_DIGEST_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail reportingScheduleJobDetail() {
    return JobBuilder.newJob(ReportingScheduleJob.class)
        .withIdentity(REPORTING_SCHEDULE_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail reportingGenerationReaperJobDetail() {
    return JobBuilder.newJob(ReportingGenerationReaperJob.class)
        .withIdentity(REPORTING_GENERATION_REAPER_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail notificationEventRetentionJobDetail() {
    return JobBuilder.newJob(NotificationEventRetentionJob.class)
        .withIdentity(NOTIFICATION_EVENT_RETENTION_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail urlAccessTokenPurgeJobDetail() {
    return JobBuilder.newJob(UrlAccessTokenPurgeJob.class)
        .withIdentity(URL_ACCESS_TOKEN_PURGE_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail credentialsConnectivityCheckJobDetail() {
    return JobBuilder.newJob(CredentialConnectivityCheckJob.class)
        .withIdentity(CREDENTIAL_CONNECTIVITY_CHECK_JOB)
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail engineDeletionReplayJobDetail() {
    return JobBuilder.newJob(EngineDeletionReplayJob.class)
        .withIdentity(ENGINE_DELETION_REPLAY_JOB)
        .storeDurably()
        .build();
  }
}
