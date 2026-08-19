package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.SchemaDimension;

public class ObjectClassGenerator extends ClassGenerator {
  private static final String objectsPackageName = "io.openaev.ocsf.objects";

  @Override
  public ClassMetadata metadata(String name, JsonNode source) {
    return new ClassMetadata(
        name,
        SchemaDimension.SINGLE_OBJECT,
        compositeOcsfClassName(name),
        objectsPackageName,
        source);
  }

  @Override
  public String emit(ClassMetadata metadata) {
    return "";
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfObject" + stringUtils.snakeToPascal(name);
  }
}
