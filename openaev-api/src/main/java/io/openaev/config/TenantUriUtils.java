package io.openaev.config;

public final class TenantUriUtils {

  public static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  public static final String TENANT_BASE_PATH = "/api/tenants/";
  public static final String TENANT_PREFIX = TENANT_BASE_PATH + "{" + TENANT_ID_PATH_VARIABLE + "}";

  private TenantUriUtils() {}
}
