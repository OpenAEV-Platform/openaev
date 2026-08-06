package io.openaev.ocsf.parser.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.source.Source;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchemaSource {
  private final Version version;
  private final PluginContext pluginContext;
  private final Map<SchemaDimension, Source> sources = new HashMap<>();

  public SchemaSource(Version version, PluginContext pluginContext) throws IOException {
    this.version = version;
    this.pluginContext = pluginContext;

    initialiseSources(version, pluginContext);
  }

  private void initialiseSources(Version version, PluginContext ctx) throws IOException {
    sources.putAll(
        Map.of(
            SchemaDimension.DICTIONARY,
            new Source(version, SchemaDimension.DICTIONARY, ctx),
            SchemaDimension.CLASSES,
            new Source(version, SchemaDimension.CLASSES, ctx),
            SchemaDimension.OBJECTS,
            new Source(version, SchemaDimension.OBJECTS, ctx),
            SchemaDimension.DATATYPES,
            new Source(version, SchemaDimension.DATATYPES, ctx)));
  }

  public JsonNode get(SchemaDimension dimension) throws IOException {
    Source src = this.sources.getOrDefault(dimension, null);
    if (src == null) {
      throw new IllegalStateException(
          "Missing initialised source for dimension %s".formatted(dimension.name()));
    }
    return src.get();
  }

  public void refreshAllSources() throws IOException {
    for (Source src : this.sources.values()) {
      src.refresh();
    }
  }
}
