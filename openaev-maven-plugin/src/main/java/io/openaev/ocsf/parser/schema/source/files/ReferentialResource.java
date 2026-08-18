package io.openaev.ocsf.parser.schema.source.files;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class ReferentialResource extends Resource {
  public ReferentialResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  public List<ResourceKey> getSubresourceKeys() throws IOException {
    try {
      JsonNode contents = read();

      List<ResourceKey> keys = new ArrayList<>();
      contents
          .elements()
          .forEachRemaining(
              element -> {
                ResourceKey resourceKey =
                    new ResourceKey(
                        element.get("name").asText(),
                        element.has("extension") ? element.get("extension").asText() : null);
                keys.add(resourceKey);
              });
      return keys;
    } catch (IOException e) {
      return List.of();
    }
  }
}
