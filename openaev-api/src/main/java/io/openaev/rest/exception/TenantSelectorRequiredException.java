package io.openaev.rest.exception;

public class TenantSelectorRequiredException extends RuntimeException {

  public TenantSelectorRequiredException() {
    super(
        "This endpoint requires an explicit tenant selector: a /{tenantId} path or an X-Tenant-Ids"
            + " header.");
  }
}
