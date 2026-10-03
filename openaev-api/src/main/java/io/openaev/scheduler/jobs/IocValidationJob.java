package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.service.stix.IocValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Drives the IOC validations after approval: evaluates the running ones from their expectation
 * results, pushes the result bundles of the finished ones, then reports every pending status to
 * OpenCTI. The order matters: a status computed in this run is reported in the same run, and a
 * final status is only reported once its results were pushed.
 *
 * <p>No job-level transaction: each step opens one short tenant transaction per validation and
 * calls OpenCTI between them, never while holding a database connection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class IocValidationJob implements Job {

  private final IocValidationService iocValidationService;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) {
    iocValidationService.computeRunningResults();
    iocValidationService.pushPendingResults();
    iocValidationService.syncPendingLifecycles();
  }
}
