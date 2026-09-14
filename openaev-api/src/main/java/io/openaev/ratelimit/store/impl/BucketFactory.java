package io.openaev.ratelimit.store.impl;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.store.LimitFactory;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BucketFactory implements LimitFactory<Bucket> {
  private final RateLimitConfig config;

  @Override
  public Bucket createLimit(LimitConsumptionRequest key) {
    Long rps = key.getSpecification().rps();
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(rps)
                .refillIntervally(rps, Duration.ofMillis(1000))
                .build())
        .build();
  }
}
