package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;

public class ObjectClassGenerator extends ClassGenerator {
  private static final String objectsPackageName = "io.openaev.ocsf.objects";

  @Override
  public ClassMetadata metadata(String name, JsonNode source) {
    return new ClassMetadata(name, compositeOcsfClassName(name), objectsPackageName);
  }

  @Override
  public String emit(ClassMetadata metadata, JsonNode source) {
    return "";
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfObject" + stringUtils.snakeToPascal(name);
  }
}
