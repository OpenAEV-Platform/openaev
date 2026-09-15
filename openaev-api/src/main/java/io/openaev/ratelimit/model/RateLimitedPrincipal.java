package io.openaev.ratelimit.model;

import io.openaev.database.model.User;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RateLimitedPrincipal {
  @Getter private final String identifier;

  public static RateLimitedPrincipal fromUser(User user) {
    if (user == null) return null;
    return new RateLimitedPrincipal(user.getId());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof RateLimitedPrincipal) {
      return identifier != null && identifier.equals(((RateLimitedPrincipal) o).getIdentifier());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier);
  }
}
