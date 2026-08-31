package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtension;
import io.openaev.ocsf.parser.schema.Version;
import io.openaev.utils.DictionaryHelper;
import io.openaev.utils.StringUtils;
import java.io.IOException;

public abstract class ClassGenerator {
  public static final String SCHEMA_PACKAGE_NAME = "io.openaev.ocsf.schema";
  protected final StringUtils stringUtils = new StringUtils();

  public abstract ClassMetadata metadata(
      Version version,
      String name,
      JsonNode source,
      OcsfSchemaExtension extension,
      String ocsfClassUid);

  public abstract String emit(ClassMetadata metadata, DictionaryHelper helper) throws IOException;
}
