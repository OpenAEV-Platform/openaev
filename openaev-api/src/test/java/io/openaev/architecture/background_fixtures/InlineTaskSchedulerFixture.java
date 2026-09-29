package io.openaev.architecture.background_fixtures;

import java.time.Instant;
import org.springframework.scheduling.TaskScheduler;

/**
 * Near-miss for the detached hand-off family through Spring's {@code TaskScheduler}: the scheduler
 * is obtained locally (here a method parameter, but equally a factory return) and used inline, with
 * NO {@code TaskScheduler} field. {@code TaskScheduler} is not a {@code
 * java.util.concurrent.Executor} subtype, so an inline detector that only accepts {@code Executor}
 * receivers misses this {@code schedule(...)} call; the detector must also accept receivers
 * assignable to {@code org.springframework.scheduling.TaskScheduler}. Test-scope only; imported
 * explicitly by the detection test.
 */
public class InlineTaskSchedulerFixture {

  public void detach(TaskScheduler scheduler) {
    scheduler.schedule(this::work, Instant.now());
  }

  private void work() {}
}
