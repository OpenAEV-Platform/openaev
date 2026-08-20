package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtension;
import io.openaev.ocsf.parser.schema.SchemaDimension;

public record ClassMetadata(
    String ocsfIdentifier,
    SchemaDimension dimension,
    OcsfSchemaExtension extension,
    String className,
    String classPackage,
    JsonNode source) {
  public String fullyQualifiedClassName() {
    return String.join(".", classPackage(), className());
  }
}
