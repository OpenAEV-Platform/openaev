package io.openaev.scheduler.jobs.notification;

import io.openaev.notification.engine.NotificationDigestService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Evaluates digest notification triggers every minute (OpenCTI digest cron equivalent). */
@Component
@RequiredArgsConstructor
public class NotificationDigestJob implements Job {

  public static final String NOTIFICATION_DIGEST_JOB = "notificationDigestJob";
  public static final String NOTIFICATION_DIGEST_TRIGGER = "notificationDigestTrigger";

  private final NotificationDigestService notificationDigestService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    notificationDigestService.runDigests(Instant.now());
  }
}
