package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.SchemaDimension;

public class ClassClassGenerator extends ClassGenerator {
  private static final String classPackageName = "io.openaev.ocsf.classes";

  @Override
  public ClassMetadata metadata(String name, JsonNode source) {
    return new ClassMetadata(
        name, SchemaDimension.SINGLE_CLASS, compositeOcsfClassName(name), classPackageName, source);
  }

  @Override
  public String emit(ClassMetadata metadata) {
    return "";
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfClass" + stringUtils.snakeToPascal(name);
  }
}
