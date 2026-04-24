package io.openaev.opencti.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Data
@Component
public class OpenCTIParamConfig {
  public static final String GRAPHQL_ENDPOINT_URI = "graphql";

  @NotNull
  @JsonProperty("enable")
  private Boolean enable;

  @NotBlank
  @JsonProperty("url")
  private String url;

  @JsonProperty("api-url")
  private String apiUrl;

  @NotBlank
  @JsonProperty("token")
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

  public String getFormattedUrl() {
    return url.endsWith("/") ? url : url + "/";
  }
}
