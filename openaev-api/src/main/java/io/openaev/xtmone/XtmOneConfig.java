package io.openaev.xtmone;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Data
public class XtmOneConfig {

  @Value("${xtm.one.url:}")
  private String url;

  @Value("${xtm.one.token:}")
  private String token;

  public boolean isConfigured() {
    return StringUtils.hasText(url) && StringUtils.hasText(token);
  }
}
