package io.openaev.stix.objects.constants;

import jakarta.validation.constraints.NotBlank;

public enum CommonProperties {
  TYPE("type"),
  SPEC_VERSION("spec_version"),
  ID("id"),
  CREATED_BY_REF("created_by_ref"),
  CREATED("created"),
  MODIFIED("modified"),
  REVOKED("revoked"),
  LABELS("labels"),
  CONFIDENCE("confidence"),
  LANG("lang"),
  EXTERNAL_REFERENCES("external_references"),
  OBJECT_MARKING_REFS("object_marking_refs"),
  GRANULAR_MARKINGS("granular_markings"),
  DEFANGED("defanged"),
  EXTENSIONS("extensions");

  private final String value;

  CommonProperties(String value) {
    this.value = value;
  }

  public static CommonProperties fromString(@NotBlank final String value) {
    for (CommonProperties prop : CommonProperties.values()) {
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
