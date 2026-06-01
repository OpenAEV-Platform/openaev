package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

@Component
public class TenantBaseListener<T extends TenantBase> {

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenant() == null) {
      entity.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    }
  }
}
