package io.openaev.scheduler.jobs.engine_sync;

import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import io.openaev.aop.LogExecutionTime;
import io.openaev.config.EngineConfig;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import io.openaev.scheduler.SelfConfiguredPlatformJob;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Slf4j
public class EngineSyncExecutionJob extends SelfConfiguredPlatformJob {
  private static final String MODEL_NAME_KEY = "modelName";

  /**
   * Caps how many model syncs may run at the same time. Every sync pass holds a database connection
   * for the whole duration of its fetch query; during a full reindex (indexing cursors reset to
   * epoch by a migration) these queries re-rank entire tables and can run for a long time. Without
   * a cap, up to {@code org.quartz.threadPool.threadCount} syncs run concurrently and, together
   * with the regular jobs and HTTP traffic, exhaust the HikariCP pool - starving the whole platform
   * until the rebuild completes. A pass that does not get a permit is simply skipped: the 15-second
   * trigger retries it shortly after, so no work is lost.
   */
  private final Semaphore concurrentSyncs;

  protected EngineSyncExecutionJob(
      Scheduler scheduler,
      EngineService engineService,
      EngineContext engineContext,
      EngineConfig engineConfig)
      throws SchedulerException {
    super(scheduler, engineService, engineContext);
    this.concurrentSyncs = new Semaphore(engineConfig.getIndexingMaxConcurrentModels());
  }

  @DisallowConcurrentExecution
  public class Job implements org.quartz.Job {
    @Override
    @LogExecutionTime
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
      String requestedModelName =
          jobExecutionContext.getMergedJobDataMap().getString(MODEL_NAME_KEY);
      Optional<EsModel<EsBase>> model =
          engineContext.getModels().stream()
              .filter(mdl -> requestedModelName.equals(mdl.getName()))
              .findFirst();

      if (model.isEmpty()) {
        throw new JobExecutionException(
            "Requested engine sync for model '%s' but no such model is known to the backend."
                .formatted(requestedModelName));
      }

      if (!concurrentSyncs.tryAcquire()) {
        log.debug(
            "Skipping engine sync for model {}: concurrency cap reached"
                + " (engine.indexing-max-concurrent-models), will retry at the next trigger",
            model.get().getName());
        return;
      }
      try {
        log.info("Executing engine sync for model {}", model.get().getName());
        engineService.bulkProcessing(Stream.of(model.get()));
      } finally {
        concurrentSyncs.release();
      }
    }
  }

  /**
   * Creates as many job definitions as there are EsModel variants. The computed jobKey is
   * specialised by model name so that quartz can prevent concurrency only in the context of a given
   * jobKey; job definitions of different jobKeys can run in parallel still.
   *
   * @return List of job definitions, one per loaded EsModel to synchronise
   */
  @Override
  protected List<JobDetail> getJobDetails() {
    List<EsModel<EsBase>> models = engineContext.getModels();
    return models.stream()
        .map(
            model ->
                JobBuilder.newJob(Job.class)
                    .storeDurably()
                    .usingJobData(MODEL_NAME_KEY, model.getName())
                    .withIdentity(
                        jobKey("EngineSyncExecutionJob_forModel_%s".formatted(model.getName())))
                    .build())
        .toList();
  }

  @Override
  protected Trigger getTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();

    return newTrigger()
        .withSchedule(_15_seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }
}
