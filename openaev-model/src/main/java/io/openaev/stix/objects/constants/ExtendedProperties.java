package io.openaev.stix.objects.constants;

import jakarta.validation.constraints.NotBlank;

public enum ExtendedProperties {
  COVERED("covered"),
  COVERAGE("coverage"),
  MITRE_EXTENSION_DEFINITION("extension-definition--322b8f77-262a-4cb8-a915-1e441e00329b");

  private final String value;

  ExtendedProperties(String value) {
    this.value = value;
  }

  public static ExtendedProperties fromString(@NotBlank final String value) {
    for (ExtendedProperties prop : ExtendedProperties.values()) {
      if (prop.value.equalsIgnoreCase(value)) {
        return prop;
      }
    }
    throw new IllegalArgumentException();
  }

  @Override
  public String toString() {
    return this.value;
  }
}
