package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.Version;

public class ClassClassGenerator extends ClassGenerator {
  @Override
  public ClassMetadata metadata(Version version, String name, JsonNode source) {
    return new ClassMetadata(
        name,
        SchemaDimension.SINGLE_CLASS,
        compositeOcsfClassName(name),
        stringUtils.toVersionedPackage(version, SCHEMA_PACKAGE_NAME, "classes"),
        source);
  }

  @Override
  public String emit(ClassMetadata metadata) {
    return "";
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfClass" + stringUtils.snakeToPascal(name);
  }
}
