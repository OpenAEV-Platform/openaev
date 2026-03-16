package io.openaev.utils.fixtures;

import io.openaev.database.model.CollectorType;

public class CollectorTypeFixture {

  public static final String DEFAULT_COLLECTOR_TYPE_NAME = "test_collector_type";

  public static CollectorType createDefaultCollectorType() {
    return new CollectorType(DEFAULT_COLLECTOR_TYPE_NAME);
  }

  public static CollectorType createCollectorType(String name) {
    return new CollectorType(name);
  }
}
