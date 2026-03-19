package io.openaev.security.token;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@NoArgsConstructor
public class PlainTokenExtractor implements ExtractorBase {

  @Override
  public String extractToken(String value) {
    return value;
  }
}
