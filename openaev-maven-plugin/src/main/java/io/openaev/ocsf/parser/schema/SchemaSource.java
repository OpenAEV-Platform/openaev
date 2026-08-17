package io.openaev.ocsf.parser.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.source.ReferentialSource;
import io.openaev.ocsf.parser.schema.source.Source;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchemaSource {
  private final Version version;
  private final PluginContext pluginContext;
  private final Map<String, Source> sources = new HashMap<>();

  public SchemaSource(Version version, PluginContext pluginContext) throws IOException {
    this.version = version;
    this.pluginContext = pluginContext;

    initialiseSourcesFromCache(version, pluginContext);
  }

  private void initialiseSourcesFromCache(Version version, PluginContext ctx) throws IOException {
    // static sources; always initialised
    sources.putAll(
        Map.of(
            SchemaDimension.DICTIONARY.name(),
            new ReferentialSource(version, SchemaDimension.DICTIONARY, ctx),
            SchemaDimension.CLASSES.name(),
            new ReferentialSource(version, SchemaDimension.CLASSES, ctx),
            SchemaDimension.OBJECTS.name(),
            new ReferentialSource(version, SchemaDimension.OBJECTS, ctx),
            SchemaDimension.DATATYPES.name(),
            new ReferentialSource(version, SchemaDimension.DATATYPES, ctx)));

    // found locally cached files for single object, single class
    for (String key :
        ((ReferentialSource) this.getSource(SchemaDimension.OBJECTS.name())).getSubsourceKeys()) {
      sources.put(key, new Source(version, SchemaDimension.SINGLE_OBJECT, ctx, key));
    }

    for (String key :
        ((ReferentialSource) this.getSource(SchemaDimension.CLASSES.name())).getSubsourceKeys()) {
      sources.put(key, new Source(version, SchemaDimension.SINGLE_CLASS, ctx, key));
    }
  }

  public JsonNode getContents(String key) throws IOException {
    return getSource(key).get();
  }

  public Source getSource(String key) {
    Source src = this.sources.getOrDefault(key, null);
    if (src == null) {
      throw new IllegalStateException("Missing initialised source for key %s".formatted(key));
    }
    return src;
  }

  public void refreshAllSources() throws IOException {
    for (Source src : this.sources.values()) {
      src.refresh();
    }
  }
}
