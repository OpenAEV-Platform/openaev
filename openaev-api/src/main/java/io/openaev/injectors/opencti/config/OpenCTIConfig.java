package io.openaev.injectors.opencti.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class OpenCTIConfig {

  @NotNull
  @Value("${openbas.xtm.opencti.enable:${openaev.xtm.opencti.enable:false}}")
  private Boolean enable;

  @NotBlank
  @Value("${openbas.xtm.opencti.url:${openaev.xtm.opencti.url:#{null}}}")
  private String url;

  @Value("${openbas.xtm.opencti.api-url:${openaev.xtm.opencti.api-url:#{null}}}")
  private String apiUrl;

  @NotBlank
  @Value("${openbas.xtm.opencti.token:${openaev.xtm.opencti.token:#{null}}}")
  private String token;

  public String getApiUrl() {
    return (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : url + "/graphql";
  }
}
