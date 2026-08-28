package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Collector;
import io.openaev.database.model.CollectorType;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.utils.fixtures.CollectorTypeFixture;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CollectorComposer extends ComposerBase<Collector> {

  @Autowired private CollectorRepository collectorRepository;
  @Autowired private CollectorTypeComposer collectorTypeComposer;

  public class Composer extends InnerComposerBase<Collector> {

    private final Collector collector;
    private Optional<SecurityPlatformComposer.Composer> securityPlatformComposer = Optional.empty();

    public Composer(Collector collector) {
      this.collector = collector;
    }

    public Composer withSecurityPlatform(SecurityPlatformComposer.Composer securityPlatform) {
      securityPlatformComposer = Optional.of(securityPlatform);
      this.collector.setSecurityPlatform(securityPlatform.get());
      return this;
    }

    @Override
    public CollectorComposer.Composer persist() {
      securityPlatformComposer.ifPresent(SecurityPlatformComposer.Composer::persist);
      // Ensure the corresponding CollectorType exists in the database
      CollectorType collectorType =
          collectorTypeComposer
              .forCollectorType(CollectorTypeFixture.createCollectorType(collector.getType()))
              .persist()
              .get();
      collector.setCollectorType(collectorType);
      // Idempotent under the composite (id, tenant_id) key: the same collector may already have
      // been created for this tenant by another wrapper in the same test. A plain save() would
      // merge against a still-null tenant_id, fail to match the existing row, and re-INSERT,
      // violating collectors_pkey. Only insert when the row is not already present.
      if (collectorRepository
          .findByIdAndTenantId(collector.getId(), TenantContext.getCurrentTenant())
          .isEmpty()) {
        collectorRepository.save(this.collector);
      }
      return this;
    }

    @Override
    public CollectorComposer.Composer delete() {
      collectorRepository.delete(this.collector);
      securityPlatformComposer.ifPresent(SecurityPlatformComposer.Composer::delete);
      return this;
    }

    @Override
    public Collector get() {
      return this.collector;
    }
  }

  public CollectorComposer.Composer forCollector(Collector collector) {
    generatedItems.add(collector);
    return new CollectorComposer.Composer(collector);
  }
}
