package io.openaev.ratelimit.config;

public class Limits {
  public static final long DEFAULT_RPS = 10L;
  public static final long AUTHENTICATED_RPS = 300L;

  public static final long TOKEN_PER_HIT = 1L;
}
