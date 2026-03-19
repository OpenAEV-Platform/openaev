package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.CollectorType;
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
      this.collectorType =
          collectorTypeRepository
              .findByName(this.collectorType.getName())
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
