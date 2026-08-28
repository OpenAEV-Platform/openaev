package io.openaev.config;

/**
 * Thrown when the statement inspector refuses a statement it cannot guarantee to filter — a shape
 * it does not cover, or SQL it cannot parse. Refusing (fail-closed) is deliberate: running such a
 * statement could leak rows across tenants.
 */
public class TenantFilteringException extends RuntimeException {

  public TenantFilteringException(String message) {
    super(message);
  }

  public TenantFilteringException(String message, Throwable cause) {
    super(message, cause);
  }
}
