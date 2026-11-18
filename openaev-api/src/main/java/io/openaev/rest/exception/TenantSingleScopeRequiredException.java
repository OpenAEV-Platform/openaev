package io.openaev.rest.exception;

/**
 * An operation that resolves one tenant's row was called with a request scope that does not hold
 * exactly one tenant. The message names the rule and the remedy, never the tenants of the scope.
 */
public class TenantSingleScopeRequiredException extends RuntimeException {

  public static final String CODE = "TENANT_SINGLE_SCOPE_REQUIRED";

  public static final String MESSAGE =
      CODE
          + ": this operation reads or changes one tenant's row, so the request scope must hold"
          + " exactly one tenant. Select it with the X-Tenant-Ids header or the"
          + " /api/tenants/{tenantId}/ route.";

  public TenantSingleScopeRequiredException() {
    super(MESSAGE);
  }
}
