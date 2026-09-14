package io.openaev.ratelimit.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@ConfigurationProperties(prefix = "openaev.ratelimit")
public class RateLimitConfig {
  @JsonProperty("store_backend")
  @Value("${openaev.ratelimit.store-backend}")
  private RateLimitStoreBackend storeBackend = RateLimitStoreBackend.IN_MEMORY;

  @JsonProperty("default_rqs")
  @Value("${openaev.ratelimit.default-rps}")
  private Long defaultRps = Limits.DEFAULT_RPS;

  @JsonProperty("authenticated_rqs")
  @Value("${openaev.ratelimit.authenticated-rps}")
  private Long authenticatedRps = Limits.AUTHENTICATED_RPS;
}
