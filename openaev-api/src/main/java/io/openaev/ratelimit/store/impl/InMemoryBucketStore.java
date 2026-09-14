package io.openaev.ratelimit.store.impl;

import static io.openaev.ratelimit.config.Limits.CONSUMPTION_RATE;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.openaev.ratelimit.store.LimitFactory;
import io.openaev.ratelimit.store.Store;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBucketStore implements Store {
  private final ConcurrentHashMap<LimitConsumptionRequest, Bucket> buckets =
      new ConcurrentHashMap<>();
  private final LimitFactory<Bucket> bucketFactory;

  public InMemoryBucketStore(LimitFactory<Bucket> bucketFactory) {
    this.bucketFactory = bucketFactory;
  }

  @Override
  public synchronized Limit tryConsume(LimitConsumptionRequest key) {
    if (!buckets.containsKey(key)) {
      buckets.put(key, bucketFactory.createLimit(key));
    }
    Bucket bucket = buckets.get(key);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(CONSUMPTION_RATE);
    return new Limit(
        bucket.getAvailableTokens(),
        probe.getRemainingTokens(),
        probe.getNanosToWaitForRefill(),
        !probe.isConsumed());
  }
}
