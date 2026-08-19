package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.utils.StringUtils;

public abstract class ClassGenerator {
  protected final StringUtils stringUtils = new StringUtils();

  public abstract ClassMetadata metadata(String name, JsonNode source);

  public abstract String emit(ClassMetadata metadata);
}
