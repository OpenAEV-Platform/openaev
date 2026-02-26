package io.openaev.context;

import io.openaev.database.model.Tenant;

public class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  public static String getCurrentTenant() {
    return Tenant.DEFAULT_TENANT_UUID;
    // return CURRENT_TENANT.get();
  }

  // TODO multi-tenancy: set with Front URL instead of default UUID and update the return above
  public static void setCurrentTenant(String tenant) {
    CURRENT_TENANT.set(tenant);
  }
}
