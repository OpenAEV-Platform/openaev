package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class EngineSyncExecutionJob implements Job {

  private final EngineService engineService;
  private final EngineContext engineContext;

  // Deliberately NOT @Transactional: bulkProcessing wraps ES/OpenSearch network bulk calls, and
  // its repository reads/writes manage their own short transactions. A job-level transaction only
  // held a DB connection open for the duration of the indexing (and never covered the parallel
  // worker threads anyway).
  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    List<EsModel<EsBase>> models = engineContext.getModels();
    log.info("Executing bulk parallel processing for {} models", models.size());
    engineService.bulkProcessing(models.stream().parallel());
  }
}
