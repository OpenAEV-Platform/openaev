package io.openaev.ratelimit.store;

import io.openaev.ratelimit.store.request.LimitConsumptionRequest;

public interface Store {
  Limit tryConsume(LimitConsumptionRequest key);
}
