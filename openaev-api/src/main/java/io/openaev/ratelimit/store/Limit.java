package io.openaev.ratelimit.store;

public interface Limit {
  LimitType getType();

  Long getLimit();

  Long getRemaining();

  Long getReset();

  Boolean getIsRateLimited();
}
