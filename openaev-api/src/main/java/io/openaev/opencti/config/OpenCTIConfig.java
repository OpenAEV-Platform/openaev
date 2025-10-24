package io.openaev.opencti.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class OpenCTIConfig {
  public static final String GRAPHQL_ENDPOINT_URI = "graphql";

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
    // Case 1: apiUrl defined
    if (apiUrl != null && !apiUrl.isBlank()) {
      return apiUrl;
    }
    // Case 2: fallback to url
    if (url == null || url.isBlank()) {
      return null;
    }
    String urlStripped = StringUtils.stripEnd(url, "/");
    if (urlStripped.toLowerCase().contains("/graphql")) {
      return urlStripped;
    }

    return String.join("/", urlStripped, GRAPHQL_ENDPOINT_URI);
  }
}
