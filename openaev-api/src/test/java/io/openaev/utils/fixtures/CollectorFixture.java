package io.openaev.utils.fixtures;

import io.openaev.database.model.Collector;

public class CollectorFixture {

  public static Collector createDefaultCollector(final String name) {
    Collector collector = new Collector();
    collector.setId(name);
    collector.setName(name);
    collector.setType(name);
    collector.setExternal(true);
    return collector;
  }
}
