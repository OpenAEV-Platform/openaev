package io.openaev.scheduler;

import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Base class for jobs that compute their own job definitions from application state.
 *
 * <p>Trigger registration is deferred to {@link #afterSingletonsInstantiated()} (i.e. once the
 * Spring context has instantiated every singleton) instead of the constructor. Subclasses derive
 * their job definitions from dynamic bean lookups (e.g. {@link EngineContext#getModels()} scans
 * {@code Handler} beans): running that lookup at construction time only sees the beans created so
 * far, which silently dropped sync jobs for every model whose handler bean had not been
 * instantiated yet.
 */
@Slf4j
public abstract class SelfConfiguredPlatformJob implements SmartInitializingSingleton {
  private final Scheduler scheduler;
  protected final EngineService engineService;
  protected final EngineContext engineContext;

  protected SelfConfiguredPlatformJob(
      Scheduler scheduler, EngineService engineService, EngineContext engineContext) {
    this.scheduler = scheduler;
    this.engineService = engineService;
    this.engineContext = engineContext;
  }

  protected abstract List<JobDetail> getJobDetails();

  /**
   * Builds the trigger for the job definition at the given index. The index lets implementations
   * stagger the start time of each trigger: triggers built with the same schedule and the same
   * start instant all fire at the exact same moment forever, which starves models behind a
   * concurrency cap (see {@code EngineSyncExecutionJob}).
   *
   * @param jobIndex position of the job definition in the {@link #getJobDetails()} list
   */
  protected abstract Trigger getTrigger(int jobIndex);

  @Override
  public void afterSingletonsInstantiated() {
    try {
      registerTriggers();
    } catch (SchedulerException e) {
      throw new IllegalStateException(
          "Failed to register triggers for " + getClass().getSimpleName(), e);
    }
  }

  private void registerTriggers() throws SchedulerException {
    List<JobDetail> jobDetails = getJobDetails();
    for (int i = 0; i < jobDetails.size(); i++) {
      scheduler.scheduleJob(jobDetails.get(i), getTrigger(i));
    }
    log.info(
        "Registered {} job(s) for {}: {}",
        jobDetails.size(),
        getClass().getSimpleName(),
        jobDetails.stream().map(jd -> jd.getKey().getName()).toList());
  }
}
