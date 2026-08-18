package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;

public record ClassMetadata(
    String ocsfIdentifier, String className, String classPackage, JsonNode source) {
  public String fullyQualifiedClassName() {
    return String.join(".", classPackage(), className());
  }
}
