package io.openaev.database.model;

import jakarta.annotation.Nullable;

/**
 * Entity that can be either platform-level ({@code tenant_id IS NULL}) or tenant-scoped ({@code
 * tenant_id NOT NULL}).
 *
 * @see TenantBase
 */
public interface DualScopeBase extends Base {

  @Nullable
  Tenant getTenant();

  void setTenant(@Nullable Tenant tenant);
}
