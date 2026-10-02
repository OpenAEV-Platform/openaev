package io.openaev.ratelimit.store;

import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.store.impl.BucketFactory;
import io.openaev.ratelimit.store.impl.InMemoryBucketStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Instantiates a store object */
@Component
@RequiredArgsConstructor
public class StoreFactory {
  private final RateLimitConfig rateLimitConfig;

  public Store fromConfiguration() {
    return switch (rateLimitConfig.getStoreBackend()) {
      case IN_MEMORY -> new InMemoryBucketStore(new BucketFactory(rateLimitConfig));
    };
  }
}
