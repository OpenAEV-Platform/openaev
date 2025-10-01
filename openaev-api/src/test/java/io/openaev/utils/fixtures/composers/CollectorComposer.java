package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Collector;
import io.openaev.database.repository.CollectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CollectorComposer extends ComposerBase<Collector> {

  @Autowired private CollectorRepository collectorRepository;

  public class Composer extends InnerComposerBase<Collector> {

    private final Collector collector;

    public Composer(Collector collector) {
      this.collector = collector;
    }

    @Override
    public CollectorComposer.Composer persist() {
      collectorRepository.save(this.collector);
      return this;
    }

    @Override
    public CollectorComposer.Composer delete() {
      collectorRepository.delete(this.collector);
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
