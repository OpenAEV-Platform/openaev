package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantBaseListener<T extends TenantBase> {

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenant() == null) {
      entity.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    }
  }

  @PreUpdate
  private void assertTenant(T entity) {
    String currentTenant = TenantContext.getCurrentTenant();
    if (currentTenant == null) {
      return;
    }
    if (entity.getTenant() != null && !currentTenant.equals(entity.getTenant().getId())) {
      throw new IllegalStateException("Tenant is immutable");
    }
  }
}
