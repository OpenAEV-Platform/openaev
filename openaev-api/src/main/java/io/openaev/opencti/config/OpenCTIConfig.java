package io.openaev.opencti.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class OpenCTIConfig {
  public static final String GRAPHQL_ENDPOINT_URI = "graphql";

  @JsonProperty("enable")
  private Boolean enable;

  @JsonProperty("url")
  private String url;

  @JsonProperty("api-url")
  private String apiUrl;

  @JsonProperty("token")
  private String token;

  public boolean isValid() {
    if (!Boolean.TRUE.equals(enable)) {
      return true;
    }
    return !StringUtils.isBlank(url) && !StringUtils.isBlank(token);
  }

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
