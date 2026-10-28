package io.openaev.rest.exception;

/**
 * The caller holds the right a request needs in the ambient tenant but not in another tenant of the
 * request scope. The message names the rule and the remedy, never the tenant that refused, so the
 * response discloses no membership beyond what the caller sent.
 */
public class TenantScopeForbiddenException extends RuntimeException {

  public static final String MESSAGE =
      "TENANT_SCOPE_FORBIDDEN: the right this request needs is missing in at least one tenant of"
          + " its scope. Select only tenants where it is held, with the X-Tenant-Ids header or the"
          + " /api/tenants/{tenantId}/ route.";

  public TenantScopeForbiddenException() {
    super(MESSAGE);
  }
}
