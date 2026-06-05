package io.openaev.context;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * Contextual scope passed explicitly to service methods. Carries the tenant ID and can be extended
 * with additional contextual information (user, locale, correlation ID, etc.) as needs evolve.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CallContext ctx = CallContext.of(tenantId);
 * myService.doSomething(ctx);
 * }</pre>
 */
@Getter
public class CallContext {

  private final @NotBlank String tenantId;

  private CallContext(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be null or blank");
    }
    this.tenantId = tenantId;
  }

  /** Create a context with just a tenant ID. */
  public static CallContext of(String tenantId) {
    return new CallContext(tenantId);
  }
}
