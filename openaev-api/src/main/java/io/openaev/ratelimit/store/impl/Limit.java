package io.openaev.ratelimit.store.impl;

import io.openaev.ratelimit.store.LimitType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Limit implements io.openaev.ratelimit.store.Limit {
  private final Long limit;
  private final Long remaining;
  private final Long reset;
  private final Boolean isRateLimited;

  @Override
  public LimitType getType() {
    return LimitType.REQUESTS_PER_SECOND;
  }
}
