package io.openaev.api.tenants;

import io.openaev.database.model.Tenant;
import java.util.Objects;

public class TenantMapper {

  private TenantMapper() {}

  public static Tenant fromInput(String tenantId, TenantInput input) {
    Objects.requireNonNull(input, "input must not be null");

    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName(input.name());
    tenant.setDescription(input.description());
    return tenant;
  }

  public static TenantOutput toOutput(Tenant tenant) {
    return new TenantOutput(
        tenant.getId(), tenant.getName(), tenant.getDescription(), tenant.getDeletedAt());
  }
}
