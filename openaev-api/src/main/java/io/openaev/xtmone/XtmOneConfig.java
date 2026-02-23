package io.openaev.xtmone;

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

  public String getEffectiveWebToken() {
    if (webToken != null && !webToken.isBlank()) {
      return webToken;
    }
    return discoveredWebToken;
  }

  public boolean isConfigured() {
    return url != null && !url.isBlank() && token != null && !token.isBlank();
  }
}
