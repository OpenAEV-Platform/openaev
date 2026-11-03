package io.openaev.ratelimit.service;

import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.store.Limit;
import io.openaev.ratelimit.store.StoreProvider;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {
  private final PreviewFeatureService previewFeatureService;
  private final RateLimitConfig rateLimitConfig;
  private final StoreProvider storeProvider;

  public Limit consume(LimitConsumptionRequest key) {
    // FIXME: remove feature flag
    if (previewFeatureService.isFeatureEnabled(PreviewFeature.RATE_LIMITING)
        && rateLimitConfig.getEnabled()) return storeProvider.getStoreBackend().tryConsume(key);
    return bypassLimit();
  }

  private Limit bypassLimit() {
    return new io.openaev.ratelimit.store.impl.Limit(-1L, -1L, -1L, false);
  }
}
