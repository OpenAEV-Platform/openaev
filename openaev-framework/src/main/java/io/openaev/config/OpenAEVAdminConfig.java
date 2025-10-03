package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class OpenAEVAdminConfig {

  @JsonProperty("admin_token")
  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String token;
}
