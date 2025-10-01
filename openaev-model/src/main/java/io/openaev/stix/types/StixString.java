package io.openaev.stix.types;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public class StixString extends BaseType<java.lang.String> {

  public StixString(java.lang.String value) {
    super(value);
  }

  public static BaseType<?> parseString(String propertyName, JsonNode propertyNode) {
    if (propertyName.endsWith("_hex")) {
      return new Hex(propertyNode.asText());
    }
    if (propertyName.equals("id")
        || propertyName.endsWith("_id")
        || propertyName.endsWith("_ref")
        || propertyName.endsWith("_refs")) {
      return new Identifier(propertyNode.asText());
    }
    if (propertyName.endsWith("_bin")) {
      return new Binary(propertyNode.asText());
    }
    if (propertyName.equals("modified") || propertyName.equals("created")) {
      return new Timestamp(Instant.parse(propertyNode.asText()));
    }
    return new StixString(propertyNode.asText());
  }
}
