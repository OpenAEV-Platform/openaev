package io.openaev.ratelimit.store.request;

import io.openaev.ratelimit.model.RateLimitedPrincipal;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LimitConsumptionRequest {
  private final RateLimitedPrincipal principal;
  private final String address;
  private final LimitSpecification specification;

  @Override
  public boolean equals(Object o) {
    if (o instanceof LimitConsumptionRequest other) {
      return address != null
          && address.equals(other.getAddress())
          && principal != null
          && principal.equals(other.getPrincipal());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(principal, address);
  }
}
