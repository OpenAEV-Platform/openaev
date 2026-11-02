package io.openaev.ratelimit.support;

import io.github.bucket4j.TimeMeter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A bucket clock that only moves when a test advances it, so token refills never depend on how
 * long the requests of a test take on the runner.
 */
public class ManualTimeMeter implements TimeMeter {
  private final AtomicLong nanos = new AtomicLong();

  @Override
  public long currentTimeNanos() {
    return nanos.get();
  }

  @Override
  public boolean isWallClockBased() {
    return false;
  }

  public void advanceMillis(long millis) {
    nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
  }

  /** Replaces the production bucket clock in the test context. */
  @TestConfiguration
  public static class Config {
    @Bean
    @Primary
    public ManualTimeMeter manualRateLimitTimeMeter() {
      return new ManualTimeMeter();
    }
  }
}
