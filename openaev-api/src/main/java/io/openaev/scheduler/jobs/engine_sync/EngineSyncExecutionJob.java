package io.openaev.scheduler.jobs.engine_sync;

import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import io.openaev.aop.LogExecutionTime;
import io.openaev.config.EngineConfig;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import io.openaev.scheduler.CustomSchedulerFactoryFactory;
import io.openaev.scheduler.SelfConfiguredPlatformJob;
import io.openaev.utils.RandomUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
// Normal run mode allows execution of engine sync jobs.
@ConditionalOnProperty(name = "openaev.run-mode", havingValue = "normal", matchIfMissing = true)
@Slf4j
public class EngineSyncExecutionJob extends SelfConfiguredPlatformJob {
  static final String SCHEDULER_INSTANCE_NAME = "OpenAEV_EngineSync_Scheduler";
  static final String MODEL_NAME_KEY = "modelName";
  static final String ENGINE_CONTEXT_INSTANCE_KEY = "engineContextInstance";
  static final String ENGINE_SERVICE_INSTANCE_KEY = "engineServiceInstance";
  static final String TENANT_TRANSACTION_INSTANCE_KEY = "tenantTxInstance";

  private final EngineContext engineContext;
  private final EngineService engineService;
  private final RandomUtils randomUtils;
  private final TenantScopedTransaction tenantTx;

  protected EngineSyncExecutionJob(
      CustomSchedulerFactoryFactory customSchedulerFactoryFactory,
      EngineService engineService,
      EngineContext engineContext,
      EngineConfig engineConfig,
      RandomUtils randomUtils,
      TenantScopedTransaction tenantTx)
      throws SchedulerException {
    super(
        customSchedulerFactoryFactory
            .get(
                SCHEDULER_INSTANCE_NAME,
                engineConfig.getIndexingMaxConcurrentModels(),
                engineConfig.getIndexingMisfireThresholdMs())
            .getScheduler());
    this.engineContext = engineContext;
    this.engineService = engineService;
    this.randomUtils = randomUtils;
    this.tenantTx = tenantTx;
  }

  @DisallowConcurrentExecution
  public static class Job implements org.quartz.Job {
    @Override
    @LogExecutionTime
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
      String requestedModelName =
          jobExecutionContext.getMergedJobDataMap().getString(MODEL_NAME_KEY);
      EngineContext engineContext =
          (EngineContext)
              jobExecutionContext.getMergedJobDataMap().get(ENGINE_CONTEXT_INSTANCE_KEY);
      EngineService engineService =
          (EngineService)
              jobExecutionContext.getMergedJobDataMap().get(ENGINE_SERVICE_INSTANCE_KEY);
      TenantScopedTransaction tenantTx =
          (TenantScopedTransaction)
              jobExecutionContext.getMergedJobDataMap().get(TENANT_TRANSACTION_INSTANCE_KEY);
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
      tenantTx.execute(
          TxCtx.allTenants(), () -> engineService.bulkProcessing(Stream.of(model.get())));
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
            model -> {
              JobDataMap jdm = new JobDataMap();
              jdm.put(MODEL_NAME_KEY, model.getName());
              jdm.put(ENGINE_CONTEXT_INSTANCE_KEY, engineContext);
              jdm.put(ENGINE_SERVICE_INSTANCE_KEY, engineService);
              jdm.put(TENANT_TRANSACTION_INSTANCE_KEY, tenantTx);
              return JobBuilder.newJob(Job.class)
                  .storeDurably()
                  .setJobData(jdm)
                  .withIdentity(
                      jobKey("EngineSyncExecutionJob_forModel_%s".formatted(model.getName())))
                  .build();
            })
        .toList();
  }

  @Override
  protected Trigger getTrigger(int delaySeconds) {
    int periodSeconds = 15;
    SimpleScheduleBuilder period =
        simpleSchedule().withIntervalInSeconds(periodSeconds).repeatForever();

    long randomMicros = randomUtils.getRandomLong(0, 999999);
    Instant startAt =
        Instant.now()
            .plus(delaySeconds % periodSeconds, ChronoUnit.SECONDS)
            .plus(randomMicros, ChronoUnit.MICROS);
    return newTrigger()
        .startAt(startAt)
        .withPriority(delaySeconds)
        .withSchedule(period.withMisfireHandlingInstructionNextWithExistingCount())
        .build();
  }
}
