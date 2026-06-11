package io.openaev.utils.fixtures;

import io.openaev.database.model.CollectorType;

public class CollectorTypeFixture {

  public static final String DEFAULT_COLLECTOR_TYPE_NAME = "test_collector_type";

  public static CollectorType createDefaultCollectorType() {
    CollectorType ct = CollectorType.fromTenant("tenant");
    ct.setName(DEFAULT_COLLECTOR_TYPE_NAME);
    return ct;
  }

  public static CollectorType createCollectorType(String name) {
    CollectorType ct = CollectorType.fromTenant("tenant");
    ct.setName(name);
    return ct;
  }
}
