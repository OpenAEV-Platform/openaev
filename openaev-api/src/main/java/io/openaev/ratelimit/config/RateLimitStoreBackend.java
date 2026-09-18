package io.openaev.ratelimit.config;

import static io.openaev.ratelimit.config.RateLimitStoreBackendValues.IN_MEMORY_STRING;

import lombok.Getter;

public enum RateLimitStoreBackend {
  IN_MEMORY(IN_MEMORY_STRING);

  @Getter private final String value;

  RateLimitStoreBackend(String value) {
    this.value = value;
  }
}
