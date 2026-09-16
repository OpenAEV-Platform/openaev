package io.openaev.scheduler;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

@Slf4j
public abstract class SelfConfiguredPlatformJob {
  private final Scheduler scheduler;

  protected SelfConfiguredPlatformJob(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  @PostConstruct
  public void register() throws SchedulerException {
    this.registerTriggers();

    if (!scheduler.isStarted()) {
      scheduler.start();
    }
  }

  protected abstract List<JobDetail> getJobDetails();

  protected abstract Trigger getTrigger(int delaySeconds);

  private void registerTriggers() throws SchedulerException {
    List<JobDetail> jobDetails = getJobDetails();
    int delaySeconds = 0;
    for (JobDetail jd : jobDetails) {
      scheduler.scheduleJob(jd, getTrigger(delaySeconds++));
    }
    log.info(
        "Registered {} job(s) for {}: {}",
        jobDetails.size(),
        getClass().getSimpleName(),
        jobDetails.stream().map(jd -> jd.getKey().getName()).toList());
  }
}
