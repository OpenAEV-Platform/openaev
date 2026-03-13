package io.openaev.database.model;

public interface TenantBase extends Base {
  Tenant getTenant();

  void setTenant(Tenant tenant);
}
