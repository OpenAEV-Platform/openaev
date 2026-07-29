package io.openaev.utils.sanitisation;

import org.apache.commons.lang3.StringUtils;

public class DomainSanitiser implements Sanitiser<String> {
  private static final String RFC1034_ALLOWED_CHARACTERS_NEGATIVE_MATCH = "[^A-Za-z0-9-.]";

  @Override
  public String sanitise(String bad) {
    if (StringUtils.isBlank(bad)) {
      return null;
    }
    return bad.replaceAll(RFC1034_ALLOWED_CHARACTERS_NEGATIVE_MATCH, "");
  }
}
