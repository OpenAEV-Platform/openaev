package io.openaev.ratelimit.exception;

import lombok.Getter;

@Getter
public class RateLimitedException extends RuntimeException {
  private final Long quota;
  private final Long remaining;
  private final Long reset;

  public RateLimitedException(String message, Long quota, Long remaining, Long reset) {
    super(message);
    this.quota = quota;
    this.remaining = remaining;
    this.reset = reset;
  }
}
