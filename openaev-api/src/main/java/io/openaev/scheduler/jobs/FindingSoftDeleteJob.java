package io.openaev.scheduler.jobs;

import io.openaev.database.repository.FindingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Soft-deletes findings that have sat manually archived for longer than the grace period: once
 * {@code finding_soft_deleted_at} is set, {@code
 * FindingDistinctSearchService#searchDistinctFindings} drops the row from the main Finding page
 * (both Active and Archived tabs), while it stays fully visible from the inject/simulation/scenario
 * views that found it (see {@code Finding#softDeletedAt} javadoc). This is a bulk column update
 * (native query, no tenant loop needed - see {@code
 * FindingRepository#softDeleteStaleArchivedFindings}), mirroring {@code
 * ExecutionTraceRetentionJob}'s single-query approach.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class FindingSoftDeleteJob implements Job {

  public static final String FINDING_SOFT_DELETE_JOB = "findingSoftDeleteJob";
  public static final String FINDING_SOFT_DELETE_TRIGGER = "findingSoftDeleteTrigger";

  private final FindingRepository findingRepository;

  @Value("${openaev.finding.soft-delete-grace-days:30}")
  private int graceDays;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Instant cutoff = Instant.now().minus(graceDays, ChronoUnit.DAYS);
    int softDeletedCount = findingRepository.softDeleteStaleArchivedFindings(cutoff);
    if (softDeletedCount > 0) {
      log.info(
          "Soft-deleted {} finding(s) archived for more than {} day(s)",
          softDeletedCount,
          graceDays);
    }
  }
}
