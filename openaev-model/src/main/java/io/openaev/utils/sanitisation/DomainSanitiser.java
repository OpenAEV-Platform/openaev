package io.openaev.utils.sanitisation;

public class DomainSanitiser implements Sanitiser<String> {
  String rfc1034AllowedCharactersNegativeMatch = "[^A-Za-z0-9\\-.]";

  @Override
  public String sanitise(String bad) {
    return bad.replaceAll(rfc1034AllowedCharactersNegativeMatch, "");
  }
}
