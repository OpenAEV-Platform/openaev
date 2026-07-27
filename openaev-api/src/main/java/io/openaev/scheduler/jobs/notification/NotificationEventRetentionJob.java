package io.openaev.scheduler.jobs.notification;

import io.openaev.notification.engine.NotificationEventRetentionService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Daily purge of old notification event outbox rows. */
@Component
@RequiredArgsConstructor
public class NotificationEventRetentionJob implements Job {

  public static final String NOTIFICATION_EVENT_RETENTION_JOB = "notificationEventRetentionJob";
  public static final String NOTIFICATION_EVENT_RETENTION_TRIGGER =
      "notificationEventRetentionTrigger";

  private final NotificationEventRetentionService retentionService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    retentionService.deleteOldEvents();
  }
}
