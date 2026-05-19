package io.openaev.database.model;

import jakarta.annotation.Nonnull;

/**
 * Entity always scoped to a single tenant ({@code tenant_id NOT NULL}). Enforced by {@code
 * TenantBaseListener} on {@code @PrePersist}.
 *
 * @see DualScopeBase
 */
public interface TenantBase extends Base {

  @Nonnull
  Tenant getTenant();

  void setTenant(@Nonnull Tenant tenant);
}
