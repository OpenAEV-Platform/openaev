package io.openaev.injectors.opencti.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.xtm.opencti")
@Data
public class OpenCTIConfig {

  @NotNull private Boolean enable;

  @NotBlank private String url;

  private String apiUrl;

  @NotBlank private String token;

  public String getApiUrl() {
    return (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : url + "/graphql";
  }
}
