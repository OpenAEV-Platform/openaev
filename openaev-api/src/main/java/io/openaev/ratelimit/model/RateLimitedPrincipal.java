package io.openaev.ratelimit.model;

import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RateLimitedPrincipal {
  @Getter private final String identifier;

  @Override
  public boolean equals(Object o) {
    if (o instanceof RateLimitedPrincipal) {
      return Objects.equals(identifier, (((RateLimitedPrincipal) o).getIdentifier()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier);
  }
}
