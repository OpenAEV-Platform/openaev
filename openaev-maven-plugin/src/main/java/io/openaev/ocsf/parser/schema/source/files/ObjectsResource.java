package io.openaev.ocsf.parser.schema.source.files;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectsResource extends ReferentialResource {
  public ObjectsResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  @Override
  public List<String> getSubresourceKeys() throws IOException {
    JsonNode contents = read();

    List<String> keys = new ArrayList<>();
    contents.elements().forEachRemaining(element -> keys.add(element.get("name").asText()));
    return keys;
  }

  @Override
  protected String getResourceName() {
    return "objects";
  }
}
