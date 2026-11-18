package io.openaev.database.model.catalog;

public enum ContentType {
  PAYLOAD,
  SCENARIO;

  // Compile-time constants for @DiscriminatorValue
  public static final String PAYLOAD_VALUE = "PAYLOAD";
  public static final String SCENARIO_VALUE = "SCENARIO";
}
