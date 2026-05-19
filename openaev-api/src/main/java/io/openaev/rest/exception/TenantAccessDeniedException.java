package io.openaev.rest.exception;

public class TenantAccessDeniedException extends RuntimeException {

  public TenantAccessDeniedException(String tenantId) {
    super("User is not a member of tenant " + tenantId);
  }
}
