package io.openaev.ocsf.parser.schema.source;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.client.OcsfApiClient;
import io.openaev.ocsf.parser.client.url.OcsfSchemaEndpoints;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.Version;
import io.openaev.ocsf.parser.schema.source.files.*;
import java.io.IOException;
import java.util.Map;

public class Source {
  private final OcsfApiClient client;
  protected Resource fileResource;
  private final Version version;
  private final SchemaDimension dimension;
  private final PluginContext ctx;
  private final String name;
  private String extension;

  private static final Map<SchemaDimension, OcsfSchemaEndpoints> dimensionToEndpointMap =
      Map.of(
          SchemaDimension.DATATYPES, OcsfSchemaEndpoints.DATATYPES,
          SchemaDimension.DICTIONARY, OcsfSchemaEndpoints.DICTIONARY,
          SchemaDimension.OBJECTS, OcsfSchemaEndpoints.OBJECTS_INVENTORY,
          SchemaDimension.SINGLE_OBJECT, OcsfSchemaEndpoints.OBJECT_SCHEMA,
          SchemaDimension.CLASSES, OcsfSchemaEndpoints.CLASSES_INVENTORY,
          SchemaDimension.SINGLE_CLASS, OcsfSchemaEndpoints.CLASS_SCHEMA);

  public Source(Version version, SchemaDimension dimension, PluginContext ctx) throws IOException {
    this(version, dimension, ctx, null, null);
  }

  public Source(
      Version version, SchemaDimension dimension, PluginContext ctx, String name, String extension)
      throws IOException {
    this.version = version;
    this.dimension = dimension;
    this.ctx = ctx;
    this.client = new OcsfApiClient(version);
    this.name = name;
    this.extension = extension;

    initialiseFileResource(dimension, ctx, name, extension);
  }

  protected void initialiseFileResource(
      SchemaDimension dimension, PluginContext ctx, String name, String extension)
      throws IOException {
    switch (dimension) {
      case DATATYPES -> this.fileResource = new DatatypesResource(version, ctx);
      case DICTIONARY -> this.fileResource = new DictionaryResource(version, ctx);
      case CLASSES -> this.fileResource = new ClassesResource(version, ctx);
      case OBJECTS -> this.fileResource = new ObjectsResource(version, ctx);
      case SINGLE_OBJECT ->
          this.fileResource = new SingleObjectResource(version, ctx, name, extension);
      case SINGLE_CLASS ->
          this.fileResource = new SingleClassResource(version, ctx, name, extension);
    }
  }

  public JsonNode get() throws IOException {
    if (!this.fileResource.fileExists()) {
      this.refresh();
    }
    return readLocalResource();
  }

  private String getFullName() {
    StringBuilder sb = new StringBuilder();
    if (this.extension != null && !this.extension.isBlank()) {
      sb.append(this.extension).append("/");
    }
    return sb.append(this.name).toString();
  }

  public void refresh() throws IOException {
    this.fileResource.write(
        client.fetch(dimensionToEndpointMap.get(this.dimension), getFullName()));
  }

  private JsonNode readLocalResource() throws IOException {
    return this.fileResource.read();
  }
}
