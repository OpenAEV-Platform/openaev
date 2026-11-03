package io.openaev.ratelimit.config;

public class Limits {
  /** Unauthenticated requests per second allowance. */
  public static final long DEFAULT_RPS = 10L;

  /** Authenticated requests per second allowance. */
  public static final long AUTHENTICATED_RPS = 300L;

  /** How many tokens in a given bucket are consumed by each request. */
  public static final long TOKEN_PER_HIT = 1L;

  /**
   * The token bucket refill period in milliseconds. This represents the time interval after which
   * the rate limiting bucket is refilled, allowing more requests to be honoured.
   *
   * <p>The value must *not* be changed; it's the basis for a "requests per second" throttling
   * model.
   */
  public static final long REFILL_PERIOD_1000MS = 1000L;
}
