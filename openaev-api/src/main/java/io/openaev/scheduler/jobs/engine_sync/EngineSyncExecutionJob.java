package io.openaev.scheduler.jobs.engine_sync;

import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import io.openaev.aop.LogExecutionTime;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import io.openaev.scheduler.SelfConfiguredPlatformJob;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EngineSyncExecutionJob extends SelfConfiguredPlatformJob {
  private static final String MODEL_NAME_KEY = "modelName";

  protected EngineSyncExecutionJob(
      Scheduler scheduler, EngineService engineService, EngineContext engineContext)
      throws SchedulerException {
    super(scheduler, engineService, engineContext);
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

      log.info("Executing engine sync for model {}", model.get().getName());
      engineService.bulkProcessing(Stream.of(model.get()));
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
