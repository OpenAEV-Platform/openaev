package io.openaev.ratelimit.service;

import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.store.Limit;
import io.openaev.ratelimit.store.StoreProvider;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {
  private final RateLimitConfig rateLimitConfig;
  private final StoreProvider storeProvider;

  public synchronized Limit consume(LimitConsumptionRequest key) {
    if (rateLimitConfig.getEnabled()) return storeProvider.getStoreBackend().tryConsume(key);
    return bypassLimit();
  }

  private Limit bypassLimit() {
    return new io.openaev.ratelimit.store.impl.Limit(-1L, -1L, -1L, false);
  }
}
