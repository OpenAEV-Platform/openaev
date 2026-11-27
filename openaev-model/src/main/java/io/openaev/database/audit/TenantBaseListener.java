package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

/**
 * Stamps a tenant-scoped insert with the tenant of the thread, and refuses one that names no tenant
 * while the thread carries none: the statement inspector gates every tenant table, so a write with
 * no tenant has no right tenant to fall back to.
 */
@Component
public class TenantBaseListener<T extends TenantBase> {

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenant() == null) {
      if (!TenantContext.hasCurrentTenant()) {
        throw new IllegalStateException(
            "unattributed tenant write: " + entity.getClass().getSimpleName());
      }
      entity.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    }
  }
}
