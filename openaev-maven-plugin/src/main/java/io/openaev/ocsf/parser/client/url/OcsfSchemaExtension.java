package io.openaev.ocsf.parser.client.url;

import java.util.Optional;
import lombok.Getter;

public enum OcsfSchemaExtension {
  LINUX("linux"),
  WIN("win"),
  MACOS("macos");

  @Getter private final String value;

  OcsfSchemaExtension(String value) {
    this.value = value;
  }

  public static Optional<OcsfSchemaExtension> fromString(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    for (OcsfSchemaExtension option : OcsfSchemaExtension.values()) {
      if (option.getValue().equals(value.toLowerCase())) {
        return Optional.of(option);
      }
    }
    return Optional.empty();
  }
}
