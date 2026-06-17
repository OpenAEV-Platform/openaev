package io.openaev.scheduler.jobs;

import io.openaev.database.repository.ExecutionTraceRepository;
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
 * Retention job for execution_traces, the fastest-growing table of the platform (one row per agent
 * execution message). Disabled by default; enable by setting a positive {@code
 * openaev.retention.execution-traces.retention-days}. Deletion is batched in short independent
 * transactions to avoid long locks on this hot-write table.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class ExecutionTraceRetentionJob implements Job {

  public static final String EXECUTION_TRACE_RETENTION_JOB = "executionTraceRetentionJob";
  public static final String EXECUTION_TRACE_RETENTION_TRIGGER = "executionTraceRetentionTrigger";

  private static final int BATCH_SIZE = 10_000;
  private static final int MAX_BATCHES_PER_RUN = 1_000;

  private final ExecutionTraceRepository executionTraceRepository;

  @Value("${openaev.retention.execution-traces.retention-days:0}")
  private int retentionDays;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    if (retentionDays <= 0) {
      return; // retention disabled (default)
    }
    Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    long totalDeleted = 0;
    for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
      int deleted = executionTraceRepository.deleteBatchOlderThan(threshold, BATCH_SIZE);
      totalDeleted += deleted;
      if (deleted < BATCH_SIZE) {
        break;
      }
    }
    if (totalDeleted > 0) {
      log.info(
          "Execution trace retention: deleted {} traces older than {} days",
          totalDeleted,
          retentionDays);
    }
  }
}
