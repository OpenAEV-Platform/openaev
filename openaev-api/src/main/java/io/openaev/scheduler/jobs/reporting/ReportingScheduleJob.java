package io.openaev.scheduler.jobs.reporting;

import io.openaev.rest.reporting.service.ReportingScheduleService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Evaluates reporting schedules every minute (same cadence and matching semantics as the
 * notification digest job). Concurrent executions are disallowed because a run can block on the
 * rendering engine for several minutes.
 */
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class ReportingScheduleJob implements Job {

  public static final String REPORTING_SCHEDULE_JOB = "reportingScheduleJob";
  public static final String REPORTING_SCHEDULE_TRIGGER = "reportingScheduleTrigger";

  private final ReportingScheduleService reportingScheduleService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    reportingScheduleService.runDueSchedules(Instant.now());
  }
}
