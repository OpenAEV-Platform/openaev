package io.openaev.ratelimit.store.request;

import static io.openaev.config.OpenAEVAnonymous.ANONYMOUS;

import io.openaev.database.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LimitConsumptionRequest {
  private final User principal;
  private final String address;
  private final LimitSpecification specification;

  public Boolean getIsAuthenticated() {
    return this.principal != null && !ANONYMOUS.equals(this.principal.getId());
  }
}
