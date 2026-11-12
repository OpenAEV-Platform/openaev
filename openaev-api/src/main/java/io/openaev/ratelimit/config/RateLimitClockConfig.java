package io.openaev.ratelimit.config;

import io.github.bucket4j.TimeMeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the rate limiting buckets measure their refill periods with.
 *
 * <p>Production uses the system wall clock in milliseconds, the bucket4j default. Tests replace
 * this bean with a manual clock so that refills happen when the test says so, not when the wall
 * clock happens to cross a period boundary.
 */
@Configuration
public class RateLimitClockConfig {
  @Bean
  public TimeMeter rateLimitTimeMeter() {
    return TimeMeter.SYSTEM_MILLISECONDS;
  }
}
