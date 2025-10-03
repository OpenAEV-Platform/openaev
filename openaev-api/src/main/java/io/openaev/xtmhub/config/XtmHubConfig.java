package io.openaev.xtmhub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class XtmHubConfig {

  @NotNull
  @Value("${openbas.xtm.hub.enable:${openaev.xtm.hub.enable:#{null}}}")
  private Boolean enable;

  @JsonProperty("url")
  @Value("${openbas.xtm.hub.url:${openaev.xtm.hub.url:#{null}}}")
  private String url;

  @JsonProperty("override_api_url")
  @Value("${openbas.xtm.hub.override-api-url:${openaev.xtm.hub.override-api-url:#{null}}}")
  private String override_api_url;

  public String getApiUrl() {
    if (StringUtils.isNotBlank(this.override_api_url)) {
      return this.override_api_url;
    }
    return this.url;
  }
}
