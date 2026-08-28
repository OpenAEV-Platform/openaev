package io.openaev.database.model;

import jakarta.annotation.Nonnull;

/**
 * Entity scoped to a single tenant via a simple {@code tenant_id} String column (part of the
 * composite primary key). Unlike {@link TenantBase}, this interface does NOT require a
 * {@code @ManyToOne Tenant} relationship — just the raw tenant ID.
 *
 * <p>Used by connector entities ({@link Collector}, {@link Injector}, {@link Executor}) where
 * {@code tenant_id} is part of the composite PK and the full {@link Tenant} object is not needed.
 */
public interface TenantIdBase extends Base {

  @Nonnull
  String getTenantId();

  void setTenantId(@Nonnull String tenantId);
}
