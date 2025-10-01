package io.openaev.xtmhub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.xtm.hub")
@Data
public class XtmHubConfig {

  @NotNull private Boolean enable;

  @JsonProperty("url")
  private String url;

  @JsonProperty("override_api_url")
  private String override_api_url;

  public String getApiUrl() {
    if (StringUtils.isNotBlank(this.override_api_url)) {
      return this.override_api_url;
    }

    return this.url;
  }
}
