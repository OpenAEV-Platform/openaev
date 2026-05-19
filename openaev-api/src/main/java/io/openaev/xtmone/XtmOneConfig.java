package io.openaev.xtmone;

import io.openaev.utils.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class XtmOneConfig {

  @Value("${openbas.xtm.one.url:${openaev.xtm.one.url:#{null}}}")
  private String url;

  @Value("${openbas.xtm.one.token:${openaev.xtm.one.token:#{null}}}")
  private String token;

  @Value("${openbas.xtm.one.web-token:${openaev.xtm.one.web-token:#{null}}}")
  private String webToken;

  private volatile String discoveredWebToken;
  private volatile String platformUrl;
  private volatile String platformVersion;

  /** Strips trailing slash from the URL so callers can safely append paths. */
  @PostConstruct
  void normalizeUrl() {
    while (url != null && url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
  }

  public String getEffectiveWebToken() {
    if (!StringUtils.isBlank(getWebToken())) {
      return getWebToken();
    }
    return getDiscoveredWebToken();
  }

  public boolean isConfigured() {
    return !StringUtils.isBlank(getUrl()) && !StringUtils.isBlank(getToken());
  }
}
