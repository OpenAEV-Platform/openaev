package io.openaev.utils.fixtures.tenants;

import io.openaev.database.model.Tenant;

public class TenantFixture {

  public static final String TENANT_NAME = "Filigran";

  public static Tenant getTenant() {
    return getTenant(TENANT_NAME);
  }

  public static Tenant getTenant(String name) {
    Tenant tenant = new Tenant();
    tenant.setName(name);
    return tenant;
  }
}
