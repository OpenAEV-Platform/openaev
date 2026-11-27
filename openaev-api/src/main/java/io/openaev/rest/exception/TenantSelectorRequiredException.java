package io.openaev.rest.exception;

public class TenantSelectorRequiredException extends RuntimeException {

  public static final String CODE = "TENANT_SELECTOR_REQUIRED";

  public TenantSelectorRequiredException() {
    super(
        "This endpoint requires an explicit tenant selector: a /{tenantId} path or an X-Tenant-Ids"
            + " header.");
  }
}
