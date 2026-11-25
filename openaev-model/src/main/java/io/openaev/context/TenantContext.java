package io.openaev.context;

import io.openaev.database.model.Tenant;

public class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  public static String getCurrentTenant() {
    String tenant = CURRENT_TENANT.get();
    return tenant != null ? tenant : Tenant.DEFAULT_TENANT_UUID;
  }

  public static boolean hasCurrentTenant() {
    return CURRENT_TENANT.get() != null;
  }

  /**
   * DO NOT USE except to set the tenant id from the URL (TenantInterceptor) AND in very specific
   * use cases before transactional annotations (like DataPack) because it could have some weird
   * behaviors inside the BackEnd
   *
   * @param tenant id
   */
  public static void setCurrentTenant(String tenant) {
    CURRENT_TENANT.set(tenant);
  }

  public static void clearCurrentTenant() {
    CURRENT_TENANT.remove();
  }
}
