package io.openaev.config;

public final class TenantUriUtils {

  public static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  public static final String TENANT_PREFIX = "/api/tenants/{" + TENANT_ID_PATH_VARIABLE + "}";

  private TenantUriUtils() {}
}
