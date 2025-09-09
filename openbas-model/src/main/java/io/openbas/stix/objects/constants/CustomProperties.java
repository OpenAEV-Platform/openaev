package io.openbas.stix.objects.constants;

import jakarta.validation.constraints.NotBlank;

public enum CustomProperties {
  COVERED("covered"),
  COVERAGE("coverage"),
  ;

  private final String value;

  CustomProperties(String value) {
    this.value = value;
  }

  public static CustomProperties fromString(@NotBlank final String value) {
    for (CustomProperties prop : CustomProperties.values()) {
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
