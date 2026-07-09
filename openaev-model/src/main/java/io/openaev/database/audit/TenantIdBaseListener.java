package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.TenantIdBase;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantIdBaseListener<T extends TenantIdBase> {

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenantId() == null) {
      entity.setTenantId(TenantContext.getCurrentTenant());
    }
  }
}
