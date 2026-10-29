package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantContext;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CollectorTypeComposer extends ComposerBase<CollectorType> {

  @Autowired private CollectorTypeRepository collectorTypeRepository;

  public class Composer extends InnerComposerBase<CollectorType> {

    private CollectorType collectorType;

    public Composer(CollectorType collectorType) {
      this.collectorType = collectorType;
    }

    @Override
    public Composer persist() {
      // The listener now fails fast on an unattributed write; stamp the ambient tenant here when
      // the caller left it unset, mirroring EndpointComposer and SecurityPlatformComposer.
      if (this.collectorType.getTenant() == null) {
        this.collectorType.setTenant(new Tenant(TenantContext.getCurrentTenant()));
      }
      String tenantId = this.collectorType.getTenant().getId();
      this.collectorType =
          collectorTypeRepository
              .findByNameAndTenantId(this.collectorType.getName(), tenantId)
              .orElseGet(() -> collectorTypeRepository.save(this.collectorType));
      return this;
    }

    @Override
    public Composer delete() {
      collectorTypeRepository.delete(this.collectorType);
      return this;
    }

    @Override
    public CollectorType get() {
      return this.collectorType;
    }
  }

  public CollectorTypeComposer.Composer forCollectorType(CollectorType collectorType) {
    generatedItems.add(collectorType);
    return new CollectorTypeComposer.Composer(collectorType);
  }
}
