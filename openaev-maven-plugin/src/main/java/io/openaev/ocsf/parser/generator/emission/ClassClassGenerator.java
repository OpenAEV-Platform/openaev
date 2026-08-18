package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;

public class ClassClassGenerator extends ClassGenerator {
  private static final String classPackageName = "io.openaev.ocsf.classes";

  @Override
  public ClassMetadata metadata(String name, JsonNode source) {
    return new ClassMetadata(name, compositeOcsfClassName(name), classPackageName);
  }

  @Override
  public String emit(ClassMetadata metadata, JsonNode source) {
    return "";
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfClass" + stringUtils.snakeToPascal(name);
  }
}
