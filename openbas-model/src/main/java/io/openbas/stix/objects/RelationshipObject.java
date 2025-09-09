package io.openbas.stix.objects;

import io.openbas.stix.types.BaseType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class RelationshipObject extends ObjectBase {
  public enum Properties {
    TARGET_REF("target_ref"),
    SOURCE_REF("source_ref"),
    START_TIME("start_time"),
    STOP_TIME("stop_time"),
    RELATIONSHIP_TYPE("relationship_type");

    private final String value;

    Properties(String value) {
      this.value = value;
    }

    public static Properties fromString(@NotBlank final String value) {
      for (Properties prop : Properties.values()) {
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

  public RelationshipObject(Map<String, BaseType<?>> properties) {
    super(properties);
  }
}
