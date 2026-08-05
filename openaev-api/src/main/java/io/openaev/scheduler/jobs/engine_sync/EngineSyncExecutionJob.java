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
import io.openaev.scheduler.SelfConfiguredPlatformJob;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
// Normal run mode allow execution of engine sync jobs
@ConditionalOnProperty(name = "openaev.run-mode", havingValue = "normal", matchIfMissing = true)
@Slf4j
public class EngineSyncExecutionJob extends SelfConfiguredPlatformJob {
  private static final String MODEL_NAME_KEY = "modelName";
  private static final int SYNC_INTERVAL_SECONDS = 15;

  /**
   * Warn every this many consecutive skipped passes (20 passes x 15s = every 5 minutes). Starved
   * models must be visible at default log level: the per-skip message is debug-only and a model
   * that never wins a permit would otherwise disappear from the logs entirely.
   */
  private static final int CONSECUTIVE_SKIPS_WARN_THRESHOLD = 20;

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

  /**
   * Consecutive skipped passes per model, reset whenever the model wins a permit. Only used to
   * surface starvation (see {@link #CONSECUTIVE_SKIPS_WARN_THRESHOLD}).
   */
  private final ConcurrentHashMap<String, AtomicInteger> consecutiveSkips =
      new ConcurrentHashMap<>();

  /**
   * The indexing sweep is genuinely-global background work: it must read rows from every tenant to
   * build the search documents. Without an explicit scope, {@code can_access_tenant} is fail-closed
   * and every tenant-activated table (e.g. {@code collectors}) silently reads empty, which dropped
   * the collector-to-security-platform attribution from every indexed expectation.
   */
  private final TenantScopedTransaction tenantTx;

  protected EngineSyncExecutionJob(
      Scheduler scheduler,
      EngineService engineService,
      EngineContext engineContext,
      EngineConfig engineConfig,
      TenantScopedTransaction tenantTx) {
    super(scheduler, engineService, engineContext);
    this.tenantTx = tenantTx;
    // Clamp to at least 1: zero (or negative) permits would silently disable engine sync
    // altogether, which is never what a tuning knob should do.
    int maxConcurrentModels = engineConfig.getIndexingMaxConcurrentModels();
    if (maxConcurrentModels < 1) {
      log.warn(
          "engine.indexing-max-concurrent-models is set to {}, which would disable engine sync"
              + " entirely; clamping to 1",
          maxConcurrentModels);
      maxConcurrentModels = 1;
    }
    this.concurrentSyncs = new Semaphore(maxConcurrentModels);
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

      String modelName = model.get().getName();
      if (!concurrentSyncs.tryAcquire()) {
        int skips =
            consecutiveSkips
                .computeIfAbsent(modelName, key -> new AtomicInteger())
                .incrementAndGet();
        if (skips % CONSECUTIVE_SKIPS_WARN_THRESHOLD == 0) {
          log.warn(
              "Engine sync for model {} has been skipped {} consecutive passes: the concurrency"
                  + " cap (engine.indexing-max-concurrent-models) is monopolised by other models",
              modelName,
              skips);
        } else {
          log.debug(
              "Skipping engine sync for model {}: concurrency cap reached"
                  + " (engine.indexing-max-concurrent-models), will retry at the next trigger",
              modelName);
        }
        return;
      }
      consecutiveSkips.remove(modelName);
      try {

        log.info("Executing engine sync for model {}", modelName);
        // allTenants(): the sweep indexes every tenant's rows; the primitive resolves it into an
        // explicit tenant list for can_access_tenant (see TxCtx#allTenants).
        tenantTx.execute(
            TxCtx.allTenants(), () -> engineService.bulkProcessing(Stream.of(model.get())));
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

  /**
   * All model triggers share the same 15-second interval, so triggers created with the same start
   * instant fire at the exact same moment on every pass, forever. Quartz fires simultaneous
   * triggers in a deterministic order (tied by trigger key, rolled once per boot), so with the
   * concurrency cap the same {@code engine.indexing-max-concurrent-models} models won every permit
   * on every pass and the remaining models were never synced at all for the lifetime of the JVM.
   * Staggering each trigger's start by one second (modulo the interval) gives every model its own
   * phase, spreading fires across the whole interval instead of one contended burst.
   */
  @Override
  protected Trigger getTrigger(int jobIndex) {
    SimpleScheduleBuilder _15_seconds =
        simpleSchedule().withIntervalInSeconds(SYNC_INTERVAL_SECONDS).repeatForever();

    return newTrigger()
        .startAt(Date.from(Instant.now().plusSeconds(jobIndex % SYNC_INTERVAL_SECONDS)))
        .withSchedule(_15_seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }
}
