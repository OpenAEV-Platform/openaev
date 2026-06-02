package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantBaseListener<T extends TenantBase> {

  @Autowired private EntityManager entityManager;

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenant() == null) {
      entity.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    }
  }
}
