package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.Version;
import io.openaev.utils.StringUtils;

public abstract class ClassGenerator {
  protected static final String SCHEMA_PACKAGE_NAME = "io.openaev.ocsf.schema";
  protected final StringUtils stringUtils = new StringUtils();

  public abstract ClassMetadata metadata(Version version, String name, JsonNode source);

  public abstract String emit(ClassMetadata metadata);
}
