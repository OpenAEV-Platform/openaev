package io.openaev.scheduler.jobs.reporting;

import io.openaev.rest.reporting.service.ReportingGenerationReaper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Sweeps the report generations stuck in a transient status every minute. Kept separate from {@link
 * ReportingScheduleJob}, which can block on the rendering engine for minutes and disallows
 * concurrent runs: the sweep must stay on time precisely when renders are misbehaving.
 */
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class ReportingGenerationReaperJob implements Job {

  public static final String REPORTING_GENERATION_REAPER_JOB = "reportingGenerationReaperJob";
  public static final String REPORTING_GENERATION_REAPER_TRIGGER =
      "reportingGenerationReaperTrigger";

  private final ReportingGenerationReaper reportingGenerationReaper;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    reportingGenerationReaper.failStuckGenerations(Instant.now());
  }
}
