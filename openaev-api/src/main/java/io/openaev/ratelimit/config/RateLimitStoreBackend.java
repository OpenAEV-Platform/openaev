package io.openaev.ratelimit.config;

public enum RateLimitStoreBackend {
  IN_MEMORY("in-memory");

  private final String value;

  RateLimitStoreBackend(String value) {
    this.value = value;
  }
}
