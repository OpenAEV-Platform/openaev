package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.SchemaDimension;

public record ClassMetadata(
    String ocsfIdentifier,
    SchemaDimension dimension,
    String className,
    String classPackage,
    JsonNode source) {
  public String fullyQualifiedClassName() {
    return String.join(".", classPackage(), className());
  }
}
