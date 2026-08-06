package io.openaev.ocsf.parser.client.url;

import lombok.Getter;

public enum OcsfSchemaExtensions {
  LINUX("linux"),
  WIN("win"),
  MACOS("macos");

  @Getter private final String value;

  OcsfSchemaExtensions(String value) {
    this.value = value;
  }
}
