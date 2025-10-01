package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.admin")
@Data
public class OpenAEVAdminConfig {
  @JsonProperty("admin_token")
  private String token;
}
