package io.openaev.scheduler.jobs.notification;

import io.openaev.service.payload_approval.PayloadApprovalNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Sends the payload approval notifications recorded since the last run, every minute: what happened
 * in the meantime is grouped into one notification per type and per user.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PayloadApprovalNotificationJob implements Job {

  public static final String PAYLOAD_APPROVAL_NOTIFICATION_JOB = "payloadApprovalNotificationJob";
  public static final String PAYLOAD_APPROVAL_NOTIFICATION_TRIGGER =
      "payloadApprovalNotificationTrigger";

  private final PayloadApprovalNotificationDispatcher payloadApprovalNotificationDispatcher;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    payloadApprovalNotificationDispatcher.dispatchPending();
  }
}
