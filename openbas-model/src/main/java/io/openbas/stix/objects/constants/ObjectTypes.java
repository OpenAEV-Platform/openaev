package io.openbas.stix.objects.constants;

import jakarta.validation.constraints.NotBlank;

public enum ObjectTypes {
  RELATIONSHIP("relationship"),
  SIGHTING("sighting"),
  IDENTITY("identity"),
  ATTACK_PATTERN("attack-pattern"),
  VULNERABILITY("vulnerability"),
  DEFAULT("__default__");

  private final String value;

  ObjectTypes(String value) {
    this.value = value;
  }

  public static ObjectTypes fromString(@NotBlank final String value) {
    for (ObjectTypes type : ObjectTypes.values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    return DEFAULT;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
