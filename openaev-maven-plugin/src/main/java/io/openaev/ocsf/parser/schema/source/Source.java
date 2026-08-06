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
  private Resource fileResource;
  private final Version version;
  private final SchemaDimension dimension;
  private final PluginContext ctx;

  private static final Map<SchemaDimension, OcsfSchemaEndpoints> dimensionToEndpointMap =
      Map.of(
          SchemaDimension.DATATYPES, OcsfSchemaEndpoints.DATATYPES,
          SchemaDimension.DICTIONARY, OcsfSchemaEndpoints.DICTIONARY,
          SchemaDimension.OBJECTS, OcsfSchemaEndpoints.OBJECTS_INVENTORY,
          SchemaDimension.CLASSES, OcsfSchemaEndpoints.CLASSES_INVENTORY);

  public Source(Version version, SchemaDimension dimension, PluginContext ctx) throws IOException {
    this.version = version;
    this.dimension = dimension;
    this.ctx = ctx;
    this.client = new OcsfApiClient();

    initialiseFileResource(dimension, ctx);
  }

  private void initialiseFileResource(SchemaDimension dimension, PluginContext ctx)
      throws IOException {
    switch (dimension) {
      case DATATYPES -> this.fileResource = new DatatypesResource(version, ctx);
      case DICTIONARY -> this.fileResource = new DictionaryResource(version, ctx);
      case CLASSES -> this.fileResource = new ClassesResource(version, ctx);
      case OBJECTS -> this.fileResource = new ObjectsResource(version, ctx);
    }
  }

  public JsonNode get() throws IOException {
    return readLocalResource();
  }

  public void refresh() throws IOException {
    this.fileResource.write(client.fetch(dimensionToEndpointMap.get(this.dimension)));
  }

  private JsonNode readLocalResource() throws IOException {
    return this.fileResource.read();
  }
}
