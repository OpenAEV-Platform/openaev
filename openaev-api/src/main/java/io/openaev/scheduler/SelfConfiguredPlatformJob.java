package io.openaev.scheduler;

import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import java.util.List;
import org.quartz.*;

public abstract class SelfConfiguredPlatformJob {
  private final Scheduler scheduler;
  protected final EngineService engineService;
  protected final EngineContext engineContext;

  protected SelfConfiguredPlatformJob(
      Scheduler scheduler, EngineService engineService, EngineContext engineContext)
      throws SchedulerException {
    this.scheduler = scheduler;
    this.engineService = engineService;
    this.engineContext = engineContext;
    registerTriggers();
  }

  protected abstract List<JobDetail> getJobDetails();

  protected abstract Trigger getTrigger();

  private void registerTriggers() throws SchedulerException {
    for (JobDetail jd : getJobDetails()) {
      scheduler.scheduleJob(jd, getTrigger());
    }
  }
}
