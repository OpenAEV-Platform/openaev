package io.openaev.scheduler.jobs.user_event;

import io.openaev.service.user_events.UserEventRetentionService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventRetentionJob implements Job {

  public static final String USER_EVENT_RETENTION_JOB = "userEventRetentionJob";
  public static final String USER_EVENT_RETENTION_TRIGGER = "userEventRetentionTrigger";

  private final UserEventRetentionService retentionService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    retentionService.deleteOldEvents();
  }
}
