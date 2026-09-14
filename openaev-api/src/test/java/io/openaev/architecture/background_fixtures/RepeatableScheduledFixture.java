package io.openaev.architecture.background_fixtures;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Near-miss for the {@code @Scheduled} family through the repeatable container: {@code @Scheduled}
 * is {@code @Repeatable}, so two of them on one method are stored by the compiler under a single
 * {@code @Schedules} container and the individual {@code @Scheduled} annotation is no longer
 * directly present. A detector that only looks for {@code @Scheduled} misses this method entirely;
 * the detector must also recognise the {@code @Schedules} container. Test-scope only; imported
 * explicitly by the detection test.
 */
public class RepeatableScheduledFixture {

  @Scheduled(fixedDelay = 10_000)
  @Scheduled(fixedDelay = 20_000)
  public void poll() {}
}
