package io.openaev.opencrq.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * The OpenCRQ a tenant is connected to (openaev.xtm.opencrq.{tenant}.*): where users open it, the
 * tRPC endpoint OpenAEV calls, and the OpenCRQ service-account token (ogrc_...) that binds the
 * calls to one OpenCRQ tenant. The token never leaves the backend.
 */
@Data
public class OpenCRQConfig {
  public static final String TRPC_ENDPOINT_URI = "trpc";

  @JsonProperty("enable")
  private Boolean enable;

  @JsonProperty("url")
  private String url;

  @JsonProperty("api-url")
  private String apiUrl;

  @JsonProperty("token")
  private String token;

  /** Enabled, with a url and a token. */
  public boolean isUsable() {
    return Boolean.TRUE.equals(enable) && !StringUtils.isAnyBlank(url, token);
  }

  /** The tRPC base of the OpenCRQ backend: api-url when set, else {url}/trpc. */
  public String getApiUrl() {
    if (apiUrl != null && !apiUrl.isBlank()) {
      return StringUtils.stripEnd(apiUrl, "/");
    }
    if (url == null || url.isBlank()) {
      return null;
    }
    return String.join("/", StringUtils.stripEnd(url, "/"), TRPC_ENDPOINT_URI);
  }

  public String getFormattedUrl() {
    return url.endsWith("/") ? url : url + "/";
  }
}
