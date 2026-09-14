package io.openaev.ratelimit.service;

import io.openaev.ratelimit.store.Limit;
import io.openaev.ratelimit.store.StoreProvider;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {
  private final StoreProvider storeProvider;

  public synchronized Limit consume(LimitConsumptionRequest key) {
    return storeProvider.getStoreBackend().tryConsume(key);
  }
}
