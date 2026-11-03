package io.openaev.ratelimit.store;

import io.openaev.ratelimit.store.request.LimitConsumptionRequest;

public interface LimitFactory<T> {
  T createLimit(LimitConsumptionRequest key);
}
