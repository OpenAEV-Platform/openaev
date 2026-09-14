package io.openaev.ratelimit.config;

import static io.openaev.ratelimit.config.RateLimitStoreBackendValues.IN_MEMORY_STRING;

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
  @Value("${openaev.ratelimit.store-backend:" + IN_MEMORY_STRING + "}")
  private RateLimitStoreBackend storeBackend;

  @JsonProperty("default_rqs")
  @Value("${default-rps:" + Limits.DEFAULT_RPS + "}")
  private Long defaultRps;

  @JsonProperty("authenticated_rqs")
  @Value("${authenticated-rps:" + Limits.AUTHENTICATED_RPS + "}")
  private Long authenticatedRps;
}
