package io.openaev.scheduler.jobs;

import io.openaev.rest.inject.service.BatchingInjectStatusService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Job to requeue all execution traces from the batching queue that need it */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class ExecutionTracesBatchRequeueJob implements Job {

  private final BatchingInjectStatusService batchingInjectStatusService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      batchingInjectStatusService.requeueCallbacks();
    } catch (IOException e) {
      log.error("Error while requeuing execution traces", e);
      throw new JobExecutionException("IO error in requeueCallbacks", e, false);
    }
  }
}
